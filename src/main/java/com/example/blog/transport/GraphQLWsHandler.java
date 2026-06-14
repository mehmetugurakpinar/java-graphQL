package com.example.blog.transport;

import com.example.blog.graphql.GraphQLProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebSocket /subscriptions endpoint — a minimal version of the "graphql-ws" protocol.
 *
 * <p>Protocol flow:</p>
 * <ol>
 *   <li>Client {@code connection_init} -> server {@code connection_ack}</li>
 *   <li>Client {@code subscribe} (id + payload.query) -> server sends {@code next} for
 *       each event and {@code complete} when the stream ends</li>
 *   <li>Client {@code complete} -> server cancels that subscription</li>
 * </ol>
 *
 * <p>Because a subscription DataFetcher returns a {@link Publisher}, here we subscribe
 * to that Publisher and forward each incoming {@link ExecutionResult} to the client.
 * Queries/mutations also work through this endpoint (a one-shot next + complete).</p>
 */
public class GraphQLWsHandler {

    private static final Logger log = LoggerFactory.getLogger(GraphQLWsHandler.class);

    private final GraphQLProvider provider;
    private final ObjectMapper mapper;

    /** sessionId -> (operationId -> subscription) — cleaned up when the connection closes. */
    private final Map<String, Map<String, Disposable>> subscriptions = new ConcurrentHashMap<>();

    public GraphQLWsHandler(GraphQLProvider provider, ObjectMapper mapper) {
        this.provider = provider;
        this.mapper = mapper;
    }

    public void register(WsConfig ws) {
        ws.onConnect(ctx -> {
            ctx.enableAutomaticPings();
            subscriptions.put(ctx.sessionId(), new ConcurrentHashMap<>());
        });
        ws.onMessage(this::onMessage);
        ws.onClose(ctx -> cancelAll(ctx.sessionId()));
        ws.onError(ctx -> cancelAll(ctx.sessionId()));
    }

    @SuppressWarnings("unchecked")
    private void onMessage(WsMessageContext ctx) {
        try {
            Map<String, Object> msg = mapper.readValue(ctx.message(), Map.class);
            String type = (String) msg.get("type");
            if (type == null) return;

            switch (type) {
                case "connection_init" -> send(ctx, Map.of("type", "connection_ack"));
                case "ping" -> send(ctx, Map.of("type", "pong"));
                case "subscribe" -> subscribe(ctx, msg);
                case "complete" -> cancel(ctx.sessionId(), (String) msg.get("id"));
                default -> log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("Failed to process WS message", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void subscribe(WsMessageContext ctx, Map<String, Object> msg) {
        String id = (String) msg.get("id");
        Map<String, Object> payload = (Map<String, Object>) msg.get("payload");
        String query = (String) payload.get("query");
        Map<String, Object> variables = (Map<String, Object>) payload.getOrDefault("variables", Map.of());
        String operationName = (String) payload.get("operationName");

        ExecutionResult result = provider.execute(query, variables, operationName);
        Object data = result.getData();

        if (data instanceof Publisher) {
            // Subscription: subscribe to the event stream
            Publisher<ExecutionResult> publisher = (Publisher<ExecutionResult>) data;
            Disposable disposable = Flux.from(publisher).subscribe(
                    eventResult -> send(ctx, Map.of("id", id, "type", "next",
                            "payload", eventResult.toSpecification())),
                    error -> send(ctx, Map.of("id", id, "type", "error",
                            "payload", error.getMessage())),
                    () -> send(ctx, Map.of("id", id, "type", "complete"))
            );
            subscriptions.getOrDefault(ctx.sessionId(), new ConcurrentHashMap<>()).put(id, disposable);
        } else {
            // Query/Mutation: a one-shot result
            send(ctx, Map.of("id", id, "type", "next", "payload", result.toSpecification()));
            send(ctx, Map.of("id", id, "type", "complete"));
        }
    }

    private void cancel(String sessionId, String operationId) {
        Map<String, Disposable> ops = subscriptions.get(sessionId);
        if (ops != null && operationId != null) {
            Disposable d = ops.remove(operationId);
            if (d != null) d.dispose();
        }
    }

    private void cancelAll(String sessionId) {
        Map<String, Disposable> ops = subscriptions.remove(sessionId);
        if (ops != null) {
            ops.values().forEach(Disposable::dispose);
        }
    }

    private void send(WsContext ctx, Map<String, Object> message) {
        try {
            ctx.send(mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Failed to send WS message", e);
        }
    }
}

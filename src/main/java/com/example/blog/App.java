package com.example.blog;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.graphql.GraphQLProvider;
import com.example.blog.rest.RestController;
import com.example.blog.transport.GraphQLHttpHandler;
import com.example.blog.transport.GraphQLWsHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Application entry point. Sets up the GraphQL engine and starts the lightweight Javalin server.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /graphql} — query + mutation</li>
 *   <li>{@code WS   /subscriptions} — subscription (graphql-ws)</li>
 *   <li>{@code GET  /graphiql} — GraphiQL UI to try things in the browser</li>
 *   <li>{@code GET  /api/...} — REST comparison endpoints</li>
 * </ul>
 */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static final int PORT = 7070;

    public static void main(String[] args) {
        Javalin app = createApp(new InMemoryDataStore());
        app.start(PORT);

        log.info("=========================================================");
        log.info("  GraphQL ready:    http://localhost:{}/graphiql", PORT);
        log.info("  GraphQL HTTP:      POST http://localhost:{}/graphql", PORT);
        log.info("  Subscriptions:     ws://localhost:{}/subscriptions", PORT);
        log.info("  REST comparison:   http://localhost:{}/api/posts", PORT);
        log.info("=========================================================");
    }

    /**
     * Builds the server but does NOT start it. This lets tests start it on a random
     * port (start(0)) and test it with real HTTP requests (REST vs GraphQL).
     */
    public static Javalin createApp(InMemoryDataStore store) {
        GraphQLProvider provider = new GraphQLProvider(store);
        ObjectMapper mapper = newMapper();

        GraphQLHttpHandler httpHandler = new GraphQLHttpHandler(provider, mapper);
        GraphQLWsHandler wsHandler = new GraphQLWsHandler(provider, mapper);
        RestController rest = new RestController(store);

        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson().updateMapper(m -> {
                    m.registerModule(new JavaTimeModule());
                    m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                }))
        );

        app.post("/graphql", httpHandler);
        app.ws("/subscriptions", wsHandler::register);
        app.get("/graphiql", ctx -> ctx.html(readResource("graphiql.html")));
        app.get("/", ctx -> ctx.redirect("/graphiql"));
        rest.register(app);

        return app;
    }

    private static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private static String readResource(String name) {
        try (InputStream in = App.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read resource: " + name, e);
        }
    }
}

package com.example.blog.transport;

import com.example.blog.graphql.GraphQLProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.util.Map;

/**
 * The POST /graphql endpoint.
 *
 * <p>GraphQL's HTTP transport is simple: the client sends a single POST body of
 * {@code { "query": "...", "variables": {...}, "operationName": "..." }}. The response
 * also follows the standard {@code { "data": ..., "errors": [...] }} shape
 * ({@link ExecutionResult#toSpecification()}).</p>
 *
 * <p>Note: unlike REST, there is a SINGLE endpoint; what is requested is determined by
 * the query in the body. Queries and mutations both go through this same endpoint.</p>
 */
public class GraphQLHttpHandler implements Handler {

    private final GraphQLProvider provider;
    private final ObjectMapper mapper;

    public GraphQLHttpHandler(GraphQLProvider provider, ObjectMapper mapper) {
        this.provider = provider;
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(Context ctx) throws Exception {
        Map<String, Object> body = mapper.readValue(ctx.body(), Map.class);
        String query = (String) body.get("query");
        Map<String, Object> variables = (Map<String, Object>) body.getOrDefault("variables", Map.of());
        String operationName = (String) body.get("operationName");

        ExecutionResult result = provider.execute(query, variables, operationName);
        ctx.json(result.toSpecification());
    }
}

package com.example.blog.graphql.error;

/**
 * Domain error thrown when a requested record cannot be found.
 *
 * <p>The {@code entity} and {@code entityId} details are written into the GraphQL
 * error's {@code extensions} by {@link CustomExceptionHandler}. This gives the client
 * machine-readable extra information (an important part of GraphQL's error contract).</p>
 */
public class NotFoundException extends RuntimeException {

    private final String entity;
    private final String entityId;

    public NotFoundException(String entity, String entityId) {
        super(entity + " not found: " + entityId);
        this.entity = entity;
        this.entityId = entityId;
    }

    public String entity() {
        return entity;
    }

    public String entityId() {
        return entityId;
    }
}

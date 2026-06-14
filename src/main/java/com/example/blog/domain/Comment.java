package com.example.blog.domain;

import java.time.OffsetDateTime;

/**
 * A comment made on a post. {@code postId} and {@code authorId} are raw references;
 * the related objects are resolved via field resolvers in the schema.
 */
public record Comment(
        String id,
        String text,
        OffsetDateTime createdAt,
        String authorId,
        String postId
) {
}

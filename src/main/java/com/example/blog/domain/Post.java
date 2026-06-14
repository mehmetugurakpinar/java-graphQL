package com.example.blog.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Blog post.
 *
 * <p>{@code authorId} holds a raw reference; the {@code Post.author} field in the
 * schema resolves the actual {@link Author} object from this id via a DataLoader
 * (avoiding N+1). Likewise, comments are resolved separately, scoped to the post.</p>
 */
public record Post(
        String id,
        String title,
        String content,
        PostStatus status,
        OffsetDateTime createdAt,
        String authorId,
        List<String> tagNames
) {
}

package com.example.blog.domain;

/**
 * Blog author / user.
 *
 * <p>Note: there is no {@code posts} field here. Related data (the author's posts)
 * is resolved separately via a "field resolver" in the schema — the domain object
 * is kept lean. This lets GraphQL resolve fields lazily as it traverses the object
 * graph.</p>
 */
public record Author(
        String id,
        String name,
        String email,
        Role role
) {
}

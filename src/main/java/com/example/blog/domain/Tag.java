package com.example.blog.domain;

/**
 * A tag. In this project a tag's id is the same as its name (a simplification).
 */
public record Tag(
        String id,
        String name
) {
}

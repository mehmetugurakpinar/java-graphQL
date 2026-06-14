package com.example.blog.domain;

/**
 * User role. Maps one-to-one with the {@code enum Role} in the schema.
 * By default graphql-java maps an enum value by the Java enum name.
 */
public enum Role {
    READER,
    AUTHOR,
    ADMIN
}

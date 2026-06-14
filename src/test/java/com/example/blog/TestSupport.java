package com.example.blog;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.graphql.GraphQLProvider;

/**
 * Shared helpers for tests. Each test uses a fresh (seeded) data store and
 * GraphQLProvider — tests do not affect one another.
 */
public final class TestSupport {

    private TestSupport() {
    }

    public static GraphQLProvider newProvider() {
        return new GraphQLProvider(new InMemoryDataStore());
    }
}

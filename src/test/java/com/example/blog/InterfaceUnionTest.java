package com.example.blog;

import com.example.blog.graphql.GraphQLProvider;
import graphql.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTERFACE (Node) and UNION (SearchResult) + inline fragment (... on Type) usage.
 */
class InterfaceUnionTest {

    private GraphQLProvider provider;

    @BeforeEach
    void setUp() {
        provider = TestSupport.newProvider();
    }

    @Test
    @DisplayName("Interface: when node(id) returns a Post, title is read via an inline fragment")
    void interfaceWithInlineFragment() {
        ExecutionResult result = provider.execute("""
                {
                  node(id: "p1") {
                    __typename
                    id
                    ... on Post { title }
                  }
                }
                """);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> node = cast(((Map<String, Object>) result.getData()).get("node"));
        assertThat(node.get("__typename")).isEqualTo("Post");
        assertThat(node.get("title")).isEqualTo("Introduction to GraphQL");
    }

    @Test
    @DisplayName("Union: search returns different types, distinguished via inline fragments")
    void unionSearchReturnsMultipleTypes() {
        ExecutionResult result = provider.execute("""
                {
                  search(term: "Introduction") {
                    __typename
                    ... on Post { title }
                    ... on Author { name }
                  }
                }
                """);

        assertThat(result.getErrors()).isEmpty();
        List<Map<String, Object>> results = cast(((Map<String, Object>) result.getData()).get("search"));
        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(r -> {
            assertThat(r.get("__typename")).isEqualTo("Post");
            assertThat(r.get("title")).isEqualTo("Introduction to GraphQL");
        });
    }

    @Test
    @DisplayName("Union: an author search returns the Author type")
    void unionSearchFindsAuthor() {
        ExecutionResult result = provider.execute("""
                {
                  search(term: "Lovelace") {
                    __typename
                    ... on Author { name }
                  }
                }
                """);

        assertThat(result.getErrors()).isEmpty();
        List<Map<String, Object>> results = cast(((Map<String, Object>) result.getData()).get("search"));
        assertThat(results).anySatisfy(r -> {
            assertThat(r.get("__typename")).isEqualTo("Author");
            assertThat(r.get("name")).isEqualTo("Ada Lovelace");
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}

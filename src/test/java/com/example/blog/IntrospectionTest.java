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
 * INTROSPECTION: a GraphQL schema makes itself queryable. Clients (GraphiQL, codegen,
 * etc.) discover types via __schema / __type. This is the basis of GraphQL's powerful,
 * self-documenting nature.
 */
class IntrospectionTest {

    private GraphQLProvider provider;

    @BeforeEach
    void setUp() {
        provider = TestSupport.newProvider();
    }

    @Test
    @DisplayName("__schema gives the names of the root types")
    void schemaRootTypes() {
        ExecutionResult result = provider.execute("""
                {
                  __schema {
                    queryType { name }
                    mutationType { name }
                    subscriptionType { name }
                  }
                }
                """);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> schema = cast(((Map<String, Object>) result.getData()).get("__schema"));
        assertThat(map(schema.get("queryType")).get("name")).isEqualTo("Query");
        assertThat(map(schema.get("mutationType")).get("name")).isEqualTo("Mutation");
        assertThat(map(schema.get("subscriptionType")).get("name")).isEqualTo("Subscription");
    }

    @Test
    @DisplayName("__type lists the fields of a type")
    void typeFields() {
        ExecutionResult result = provider.execute("""
                {
                  __type(name: "Post") {
                    name
                    kind
                    fields { name }
                  }
                }
                """);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> type = cast(((Map<String, Object>) result.getData()).get("__type"));
        assertThat(type.get("kind")).isEqualTo("OBJECT");
        List<Map<String, Object>> fields = cast(type.get("fields"));
        assertThat(fields).extracting(f -> f.get("name"))
                .contains("id", "title", "content", "author", "comments");
    }

    @Test
    @DisplayName("A @deprecated field shows up as deprecated in introspection")
    void deprecatedFieldVisible() {
        ExecutionResult result = provider.execute("""
                {
                  __type(name: "Post") {
                    fields(includeDeprecated: true) { name isDeprecated }
                  }
                }
                """);

        Map<String, Object> type = cast(((Map<String, Object>) result.getData()).get("__type"));
        List<Map<String, Object>> fields = cast(type.get("fields"));
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.get("name")).isEqualTo("summary");
            assertThat(f.get("isDeprecated")).isEqualTo(true);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }
}

package com.example.blog;

import com.example.blog.graphql.GraphQLProvider;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERROR HANDLING: GraphQL errors are returned in the "errors" array with message +
 * path + extensions; moreover, other fields can still return successfully (partial result).
 */
class ErrorHandlingTest {

    private GraphQLProvider provider;

    @BeforeEach
    void setUp() {
        provider = TestSupport.newProvider();
    }

    @Test
    @DisplayName("NotFound: errors[] + extensions(code, entity) are populated")
    void notFoundProducesStructuredError() {
        ExecutionResult result = provider.execute(
                "mutation { publishPost(id: \"no-such-id\") { id } }");

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("not found");
        assertThat(error.getExtensions())
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("entity", "Post")
                .containsEntry("entityId", "no-such-id");

        // since publishPost is non-null, the value becomes null (data as a whole may be null)
        Map<String, Object> data = result.getData();
        assertThat(data == null || data.get("publishPost") == null).isTrue();
    }

    @Test
    @DisplayName("Partial result: one field errors while another returns successfully")
    void partialResults() {
        ExecutionResult result = provider.execute("""
                {
                  ok: post(id: "p1") { title }
                  bad: node(id: "nope") { id }
                }
                """);

        // the 'ok' field returned data
        Map<String, Object> data = result.getData();
        Map<String, Object> ok = cast(data.get("ok"));
        assertThat(ok.get("title")).isEqualTo("Introduction to GraphQL");

        // the 'bad' field is null + a single error whose path points to 'bad'
        assertThat(data.get("bad")).isNull();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getPath()).containsExactly("bad");
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}

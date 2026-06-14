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
 * QUERY fundamentals: field selection, arguments, enums, aliases, fragments,
 * nested fields, __typename and variables.
 */
class QueryTest {

    private GraphQLProvider provider;

    @BeforeEach
    void setUp() {
        provider = TestSupport.newProvider();
    }

    @Test
    @DisplayName("The client only gets the fields it requests (no over-fetching)")
    void selectsOnlyRequestedFields() {
        ExecutionResult result = provider.execute("{ post(id: \"p1\") { title } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        Map<String, Object> post = cast(data.get("post"));
        // Only 'title' is in the response — content, status, etc. did not come back.
        assertThat(post).containsOnlyKeys("title");
        assertThat(post.get("title")).isEqualTo("Introduction to GraphQL");
    }

    @Test
    @DisplayName("Arguments + filter + pagination")
    void argumentsFilterAndPaging() {
        ExecutionResult result = provider.execute(
                "{ posts(filter: { status: PUBLISHED }, limit: 2, offset: 0) { id status } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        List<Map<String, Object>> posts = cast(data.get("posts"));
        assertThat(posts).hasSize(2);
        // enum output: the String "PUBLISHED"
        assertThat(posts).allSatisfy(p -> assertThat(p.get("status")).isEqualTo("PUBLISHED"));
    }

    @Test
    @DisplayName("Alias: querying the same field twice under different names")
    void aliases() {
        ExecutionResult result = provider.execute(
                "{ first: post(id: \"p1\") { title } second: post(id: \"p2\") { title } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data).containsKeys("first", "second");
        assertThat(map(data.get("first")).get("title")).isEqualTo("Introduction to GraphQL");
        assertThat(map(data.get("second")).get("title")).isEqualTo("Differences from REST");
    }

    @Test
    @DisplayName("Fragment: reusing a repeated set of fields")
    void fragments() {
        ExecutionResult result = provider.execute("""
                {
                  post(id: "p1") { ...PostFields }
                }
                fragment PostFields on Post { title status }
                """);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        assertThat(post).containsKeys("title", "status");
    }

    @Test
    @DisplayName("Nested: post + author + comments in a single query")
    void nestedGraphTraversal() {
        ExecutionResult result = provider.execute("""
                {
                  post(id: "p1") {
                    title
                    author { name role }
                    comments { text author { name } }
                  }
                }
                """);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        Map<String, Object> author = cast(post.get("author"));
        assertThat(author.get("name")).isEqualTo("Ada Lovelace");
        assertThat(author.get("role")).isEqualTo("ADMIN");
        List<Map<String, Object>> comments = cast(post.get("comments"));
        assertThat(comments).isNotEmpty();
        assertThat(comments.get(0)).containsKeys("text", "author");
    }

    @Test
    @DisplayName("The __typename meta field")
    void typenameMetaField() {
        ExecutionResult result = provider.execute("{ post(id: \"p1\") { __typename } }");
        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        assertThat(post.get("__typename")).isEqualTo("Post");
    }

    @Test
    @DisplayName("Variables: query parameters are provided externally")
    void variables() {
        String query = "query GetPost($id: ID!) { post(id: $id) { title } }";
        ExecutionResult result = provider.execute(query, Map.of("id", "p2"), "GetPost");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        assertThat(post.get("title")).isEqualTo("Differences from REST");
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

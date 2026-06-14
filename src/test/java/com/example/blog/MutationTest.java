package com.example.blog;

import com.example.blog.graphql.GraphQLProvider;
import graphql.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MUTATION: writing via input types, selecting fields from the returned type,
 * default values, and using the logged-in user from GraphQLContext.
 */
class MutationTest {

    private GraphQLProvider provider;

    @BeforeEach
    void setUp() {
        provider = TestSupport.newProvider();
    }

    @Test
    @DisplayName("createPost: new post via input type; status defaults to DRAFT")
    void createPostWithInput() {
        String mutation = """
                mutation Create($input: CreatePostInput!) {
                  createPost(input: $input) { id title status }
                }
                """;
        Map<String, Object> input = Map.of(
                "title", "New Post",
                "content", "Content...",
                "authorId", "a2");

        ExecutionResult result = provider.execute(mutation, Map.of("input", input), "Create");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> created = cast(((Map<String, Object>) result.getData()).get("createPost"));
        assertThat(created.get("title")).isEqualTo("New Post");
        assertThat(created.get("status")).isEqualTo("DRAFT"); // the default value
        assertThat(created.get("id")).isNotNull();
    }

    @Test
    @DisplayName("publishPost: status DRAFT -> PUBLISHED")
    void publishPost() {
        // first create a draft
        Map<String, Object> input = Map.of("title", "Draft", "content", "x", "authorId", "a1");
        ExecutionResult created = provider.execute(
                "mutation($i: CreatePostInput!){ createPost(input:$i){ id status } }",
                Map.of("i", input), null);
        String id = (String) map(((Map<String, Object>) created.getData()).get("createPost")).get("id");

        ExecutionResult published = provider.execute(
                "mutation($id: ID!){ publishPost(id:$id){ id status } }",
                Map.of("id", id), null);

        assertThat(published.getErrors()).isEmpty();
        Map<String, Object> post = cast(((Map<String, Object>) published.getData()).get("publishPost"));
        assertThat(post.get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("addComment: the author comes from the logged-in user in GraphQLContext")
    void addCommentUsesContextUser() {
        String mutation = """
                mutation Add($input: AddCommentInput!) {
                  addComment(input: $input) { text author { id name } }
                }
                """;
        Map<String, Object> input = Map.of("postId", "p1", "text", "Awesome!");

        ExecutionResult result = provider.execute(mutation, Map.of("input", input), "Add");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> comment = cast(((Map<String, Object>) result.getData()).get("addComment"));
        assertThat(comment.get("text")).isEqualTo("Awesome!");
        // currentUserId in the context = "a1" (Ada Lovelace)
        Map<String, Object> author = cast(comment.get("author"));
        assertThat(author.get("id")).isEqualTo("a1");
        assertThat(author.get("name")).isEqualTo("Ada Lovelace");
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

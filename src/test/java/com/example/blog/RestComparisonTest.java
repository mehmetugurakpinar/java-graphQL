package com.example.blog;

import com.example.blog.data.InMemoryDataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST vs GraphQL — a comparison using real HTTP requests.
 *
 * <p>It concretely demonstrates two typical REST problems:</p>
 * <ul>
 *   <li><b>Over-fetching:</b> REST {@code /api/posts/{id}} returns ALL fields of the
 *       post; GraphQL returns only the selected ones.</li>
 *   <li><b>Under-fetching:</b> displaying a post with its author + comments requires
 *       3 separate requests in REST; in GraphQL a single request suffices.</li>
 * </ul>
 */
class RestComparisonTest {

    private static Javalin app;
    private static HttpClient client;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String base;

    @BeforeAll
    static void startServer() {
        app = App.createApp(new InMemoryDataStore());
        app.start(0); // random port
        base = "http://localhost:" + app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (app != null) app.stop();
    }

    @Test
    @DisplayName("Over-fetching: REST returns all fields")
    void restOverFetches() throws Exception {
        Map<String, Object> post = getJson("/api/posts/p1");
        // Even if the client only wants the title, REST sends everything:
        assertThat(post).containsKeys("id", "title", "content", "status", "createdAt", "authorId", "tagNames");
    }

    @Test
    @DisplayName("GraphQL returns only the selected fields (no over-fetching)")
    void graphqlSelectsFields() throws Exception {
        Map<String, Object> data = graphql("{ post(id: \"p1\") { title } }");
        Map<String, Object> post = cast(data.get("post"));
        assertThat(post).containsOnlyKeys("title");
    }

    @Test
    @DisplayName("Under-fetching: 3 requests in REST vs 1 request in GraphQL")
    void restNeedsManyRoundTrips() throws Exception {
        int restCalls = 0;

        // 1) get the post
        Map<String, Object> post = getJson("/api/posts/p1");
        restCalls++;
        // 2) get the post's author (a separate request)
        getJson("/api/authors/" + post.get("authorId"));
        restCalls++;
        // 3) get the post's comments (a separate request)
        getJsonArrayIgnore("/api/posts/p1/comments");
        restCalls++;

        assertThat(restCalls).isEqualTo(3);

        // GraphQL: the same information in a SINGLE request
        int graphqlCalls = 0;
        Map<String, Object> data = graphql(
                "{ post(id: \"p1\") { title author { name } comments { text } } }");
        graphqlCalls++;

        Map<String, Object> gqlPost = cast(data.get("post"));
        assertThat(gqlPost).containsKeys("title", "author", "comments");
        assertThat(graphqlCalls).isEqualTo(1);
        assertThat(graphqlCalls).isLessThan(restCalls);
    }

    // ---- HTTP helpers ----

    private Map<String, Object> getJson(String path) throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return MAPPER.readValue(resp.body(), Map.class);
    }

    private void getJsonArrayIgnore(String path) throws Exception {
        client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphql(String query) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("query", query));
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(base + "/graphql"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> envelope = MAPPER.readValue(resp.body(), Map.class);
        return (Map<String, Object>) envelope.get("data");
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}

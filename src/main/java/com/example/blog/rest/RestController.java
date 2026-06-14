package com.example.blog.rest;

import com.example.blog.data.InMemoryDataStore;
import io.javalin.Javalin;

/**
 * Classic REST endpoints — for COMPARISON with GraphQL.
 *
 * <p>Serves the same blog data in a REST style. The README and {@code RestComparisonTest}
 * highlight these differences:</p>
 * <ul>
 *   <li><b>Over-fetching:</b> {@code GET /api/posts} returns ALL fields of each post;
 *       the client gets more than it needs even if it only wants the title. In GraphQL
 *       only the selected fields are returned.</li>
 *   <li><b>Under-fetching / N+1 round trips:</b> to display a post with its author and
 *       comments, REST needs 3 separate requests
 *       ({@code /api/posts/{id}}, {@code /api/authors/{authorId}},
 *       {@code /api/posts/{id}/comments}); in GraphQL a single query suffices.</li>
 * </ul>
 */
public class RestController {

    private final InMemoryDataStore store;

    public RestController(InMemoryDataStore store) {
        this.store = store;
    }

    public void register(Javalin app) {
        // All posts (with all fields -> over-fetching)
        app.get("/api/posts", ctx ->
                ctx.json(store.listPosts(null, null, null, null, 100, 0)));

        // A single post
        app.get("/api/posts/{id}", ctx -> {
            var post = store.findPost(ctx.pathParam("id")).orElse(null);
            if (post == null) {
                ctx.status(404).json(java.util.Map.of("error", "Post not found"));
            } else {
                ctx.json(post);
            }
        });

        // A post's comments (a separate request -> under-fetching)
        app.get("/api/posts/{id}/comments", ctx ->
                ctx.json(store.commentsForPost(ctx.pathParam("id"))));

        // A single author (a separate request to get the post's author -> under-fetching)
        app.get("/api/authors/{id}", ctx -> {
            var author = store.findAuthor(ctx.pathParam("id")).orElse(null);
            if (author == null) {
                ctx.status(404).json(java.util.Map.of("error", "Author not found"));
            } else {
                ctx.json(author);
            }
        });

        // All authors
        app.get("/api/authors", ctx -> ctx.json(store.allAuthors()));
    }
}

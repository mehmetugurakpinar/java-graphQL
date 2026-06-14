package com.example.blog.graphql.fetchers;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.domain.Comment;
import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.graphql.error.NotFoundException;
import graphql.schema.DataFetcher;

import java.util.List;
import java.util.Map;

/**
 * DataFetchers that resolve the fields of the {@code Mutation} root type.
 *
 * <p>By default graphql-java delivers input types as {@code Map<String,Object>}.
 * The "currentUserId" (the logged-in user) carried via {@code GraphQLContext} is
 * also used here as an example.</p>
 */
public class MutationFetchers {

    private final InMemoryDataStore store;

    public MutationFetchers(InMemoryDataStore store) {
        this.store = store;
    }

    /** createPost(input: CreatePostInput!): Post! */
    @SuppressWarnings("unchecked")
    public DataFetcher<Post> createPost() {
        return env -> {
            Map<String, Object> input = env.getArgument("input");
            String title = (String) input.get("title");
            String content = (String) input.get("content");
            String authorId = (String) input.get("authorId");
            Object statusObj = input.get("status");
            PostStatus status = statusObj == null ? PostStatus.DRAFT : PostStatus.valueOf(statusObj.toString());
            List<String> tagNames = (List<String>) input.getOrDefault("tagNames", List.of());
            return store.createPost(title, content, authorId, status, tagNames);
        };
    }

    /** publishPost(id: ID!): Post! — throws NotFoundException if not found (error example). */
    public DataFetcher<Post> publishPost() {
        return env -> {
            String id = env.getArgument("id");
            Post published = store.publishPost(id);
            if (published == null) {
                throw new NotFoundException("Post", id);
            }
            return published;
        };
    }

    /**
     * addComment(input: AddCommentInput!): Comment!
     * The comment's author is taken from the logged-in user in GraphQLContext (the
     * input has no authorId — identity is resolved on the server side).
     * This operation also triggers the commentAdded subscription.
     */
    public DataFetcher<Comment> addComment() {
        return env -> {
            Map<String, Object> input = env.getArgument("input");
            String postId = (String) input.get("postId");
            String text = (String) input.get("text");
            String currentUserId = env.getGraphQlContext().getOrDefault("currentUserId", "a1");
            return store.addComment(postId, text, currentUserId);
        };
    }
}

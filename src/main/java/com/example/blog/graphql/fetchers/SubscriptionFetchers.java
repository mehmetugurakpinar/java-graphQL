package com.example.blog.graphql.fetchers;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.domain.Comment;
import graphql.schema.DataFetcher;
import org.reactivestreams.Publisher;

/**
 * DataFetchers that resolve the fields of the {@code Subscription} root type.
 *
 * <p>A subscription DataFetcher returns a {@link Publisher} (reactive-streams)
 * instead of a single value. graphql-java processes each published event against
 * the subscription field's selection set and forwards it to the client.</p>
 */
public class SubscriptionFetchers {

    private final InMemoryDataStore store;

    public SubscriptionFetchers(InMemoryDataStore store) {
        this.store = store;
    }

    /**
     * commentAdded(postId: ID!): Comment!
     * Publishes an event for each comment added to the given post.
     */
    public DataFetcher<Publisher<Comment>> commentAdded() {
        return env -> {
            String postId = env.getArgument("postId");
            return store.commentStream(postId); // Flux -> Publisher
        };
    }
}

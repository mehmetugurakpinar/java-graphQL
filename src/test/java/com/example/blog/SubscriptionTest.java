package com.example.blog;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.graphql.GraphQLProvider;
import graphql.ExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SUBSCRIPTION: a subscription DataFetcher returns a Publisher. Here we subscribe at
 * the engine level and verify, with StepVerifier, that an event is published when a
 * new comment is added.
 */
class SubscriptionTest {

    @Test
    @DisplayName("commentAdded: an event is published when a comment is added to the post")
    void commentAddedEmitsEvent() {
        InMemoryDataStore store = new InMemoryDataStore();
        GraphQLProvider provider = new GraphQLProvider(store);

        ExecutionResult result = provider.execute(
                "subscription { commentAdded(postId: \"p1\") { id text } }");

        assertThat(result.getErrors()).isEmpty();
        // The data of a subscription result is a Publisher.
        Publisher<ExecutionResult> publisher = result.getData();

        StepVerifier.create(Flux.from(publisher))
                // after subscribing, add a comment to p1 -> the event is triggered
                .then(() -> store.addComment("p1", "Live comment", "a2"))
                .assertNext(event -> {
                    Map<String, Object> data = event.getData();
                    Map<String, Object> comment = cast(data.get("commentAdded"));
                    assertThat(comment.get("text")).isEqualTo("Live comment");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("A comment added to a different post does not trigger this subscription (filter)")
    void filtersByPostId() {
        InMemoryDataStore store = new InMemoryDataStore();
        GraphQLProvider provider = new GraphQLProvider(store);

        ExecutionResult result = provider.execute(
                "subscription { commentAdded(postId: \"p1\") { text } }");
        Publisher<ExecutionResult> publisher = result.getData();

        StepVerifier.create(Flux.from(publisher))
                .then(() -> store.addComment("p2", "Other post", "a2"))   // p2 -> must not arrive
                .then(() -> store.addComment("p1", "Correct post", "a3")) // p1 -> must arrive
                .assertNext(event -> {
                    Map<String, Object> comment = cast(((Map<String, Object>) event.getData()).get("commentAdded"));
                    assertThat(comment.get("text")).isEqualTo("Correct post");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}

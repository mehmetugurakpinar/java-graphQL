package com.example.blog;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.graphql.GraphQLProvider;
import graphql.ExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DATALOADER and the N+1 problem.
 *
 * <p>The query "{@code posts { author { name } } }": all posts + the author of each.
 * The naive approach issues a separate author query per post (N+1). DataLoader, on
 * the other hand, collects all author ids and resolves them in a SINGLE batch.</p>
 *
 * <p>We PROVE the difference by measuring the number of "round trips" made to the
 * data source.</p>
 */
class DataLoaderTest {

    private static final String QUERY = "{ posts { author { name } } }";

    @Test
    @DisplayName("With DataLoader: all authors are resolved in ONE batch (few round trips)")
    void batchedResolutionAvoidsNPlusOne() {
        InMemoryDataStore store = new InMemoryDataStore();
        GraphQLProvider provider = new GraphQLProvider(store); // default: DataLoader

        store.resetRoundTrips();
        ExecutionResult result = provider.execute(QUERY);

        assertThat(result.getErrors()).isEmpty();
        // 1) listPosts + 2) authorsByIds (a single batch) = 2 round trips
        assertThat(store.dbRoundTrips()).isEqualTo(2);
    }

    @Test
    @DisplayName("Naive resolution: a separate author query per post (N+1)")
    void naiveResolutionCausesNPlusOne() {
        InMemoryDataStore store = new InMemoryDataStore();
        GraphQLProvider provider = new GraphQLProvider(store, true); // naive Post.author

        store.resetRoundTrips();
        ExecutionResult result = provider.execute(QUERY);

        assertThat(result.getErrors()).isEmpty();
        // 1) listPosts + 4) one findAuthor per post (4 posts) = 5 round trips
        assertThat(store.dbRoundTrips()).isEqualTo(5);
    }

    @Test
    @DisplayName("In conclusion, DataLoader makes fewer round trips than the naive approach")
    void dataLoaderUsesFewerRoundTrips() {
        InMemoryDataStore batchedStore = new InMemoryDataStore();
        new GraphQLProvider(batchedStore).execute(QUERY);
        int batched = batchedStore.dbRoundTrips();

        InMemoryDataStore naiveStore = new InMemoryDataStore();
        new GraphQLProvider(naiveStore, true).execute(QUERY);
        int naive = naiveStore.dbRoundTrips();

        assertThat(batched).isLessThan(naive);
    }
}

package com.example.blog.graphql.dataloaders;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.domain.Author;
import com.example.blog.domain.Comment;
import com.example.blog.domain.Post;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Builds the DataLoader registry.
 *
 * <p><b>The N+1 problem:</b> if we list 10 posts and request each one's author, the
 * naive approach makes 1 (list) + 10 (authors) = 11 queries. DataLoader collects all
 * author-id requests within the same tick and resolves them with a SINGLE batch call:
 * 1 + 1 = 2 queries.</p>
 *
 * <p><b>Important:</b> a DataLoader's cache is per request. Therefore the registry is
 * created AGAIN for EVERY GraphQL request — no data leaks between requests.</p>
 */
public final class DataLoaderRegistryFactory {

    public static final String AUTHOR_LOADER = "authorById";
    public static final String COMMENTS_LOADER = "commentsByPostId";
    public static final String POSTS_LOADER = "postsByAuthorId";

    private DataLoaderRegistryFactory() {
    }

    public static DataLoaderRegistry create(InMemoryDataStore store) {
        DataLoaderRegistry registry = new DataLoaderRegistry();

        // 1) Batch-load authors by id: Set<id> -> Map<id, Author>
        MappedBatchLoader<String, Author> authorBatch = (Set<String> ids) ->
                CompletableFuture.completedFuture(store.findAuthorsByIds(new ArrayList<>(ids)));
        registry.register(AUTHOR_LOADER, DataLoaderFactory.newMappedDataLoader(authorBatch));

        // 2) Batch-load comments by post id: Set<postId> -> Map<postId, List<Comment>>
        MappedBatchLoader<String, List<Comment>> commentsBatch = (Set<String> postIds) ->
                CompletableFuture.completedFuture(store.findCommentsByPostIds(new ArrayList<>(postIds)));
        registry.register(COMMENTS_LOADER, DataLoaderFactory.newMappedDataLoader(commentsBatch));

        // 3) Batch-load posts by author id: Set<authorId> -> Map<authorId, List<Post>>
        MappedBatchLoader<String, List<Post>> postsBatch = (Set<String> authorIds) ->
                CompletableFuture.completedFuture(store.findPostsByAuthorIds(new ArrayList<>(authorIds)));
        registry.register(POSTS_LOADER, DataLoaderFactory.newMappedDataLoader(postsBatch));

        return registry;
    }
}

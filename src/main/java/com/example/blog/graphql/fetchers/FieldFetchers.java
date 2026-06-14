package com.example.blog.graphql.fetchers;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.domain.Author;
import com.example.blog.domain.Comment;
import com.example.blog.domain.Post;
import com.example.blog.domain.Tag;
import com.example.blog.graphql.dataloaders.DataLoaderRegistryFactory;
import graphql.schema.DataFetcher;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Resolvers for related (nested) fields.
 *
 * <p>This class demonstrates GraphQL's most powerful idea: every field can have its
 * own resolver. Relations such as {@code Post.author}, {@code Post.comments} and
 * {@code Author.posts} are resolved separately, on demand.</p>
 *
 * <p>{@link #postAuthor()} batches via DataLoader (no N+1).
 * {@link #postAuthorNaive()} deliberately issues a separate query per post (to show
 * the N+1 problem) — the two are compared in {@code DataLoaderTest}.</p>
 */
public class FieldFetchers {

    private final InMemoryDataStore store;

    public FieldFetchers(InMemoryDataStore store) {
        this.store = store;
    }

    // -------- Post fields --------

    /** Post.author — batched via DataLoader (efficient). */
    public DataFetcher<CompletableFuture<Author>> postAuthor() {
        return env -> {
            Post post = env.getSource();
            DataLoader<String, Author> loader = env.getDataLoader(DataLoaderRegistryFactory.AUTHOR_LOADER);
            return loader.load(post.authorId());
        };
    }

    /** Post.author — NAIVE version: a separate query per post (exhibits N+1). */
    public DataFetcher<Author> postAuthorNaive() {
        return env -> {
            Post post = env.getSource();
            return store.findAuthor(post.authorId()).orElse(null);
        };
    }

    /** Post.comments — batched via DataLoader. */
    public DataFetcher<CompletableFuture<List<Comment>>> postComments() {
        return env -> {
            Post post = env.getSource();
            DataLoader<String, List<Comment>> loader =
                    env.getDataLoader(DataLoaderRegistryFactory.COMMENTS_LOADER);
            return loader.load(post.id());
        };
    }

    /** Post.tags — converts tag names into Tag objects (no loader needed). */
    public DataFetcher<List<Tag>> postTags() {
        return env -> {
            Post post = env.getSource();
            return post.tagNames().stream()
                    .map(name -> new Tag(name, name))
                    .collect(Collectors.toList());
        };
    }

    /** Post.shoutTitle — returns the title; the @uppercase directive upper-cases it. */
    public DataFetcher<String> postShoutTitle() {
        return env -> {
            Post post = env.getSource();
            return post.title();
        };
    }

    /** Post.summary — an @deprecated field example; returns a short summary of the content. */
    public DataFetcher<String> postSummary() {
        return env -> {
            Post post = env.getSource();
            String c = post.content();
            return c.length() <= 30 ? c : c.substring(0, 30) + "...";
        };
    }

    // -------- Author fields --------

    /** Author.posts — batched via DataLoader. */
    public DataFetcher<CompletableFuture<List<Post>>> authorPosts() {
        return env -> {
            Author author = env.getSource();
            DataLoader<String, List<Post>> loader =
                    env.getDataLoader(DataLoaderRegistryFactory.POSTS_LOADER);
            return loader.load(author.id());
        };
    }

    // -------- Comment fields --------

    /** Comment.author — batched via DataLoader. */
    public DataFetcher<CompletableFuture<Author>> commentAuthor() {
        return env -> {
            Comment comment = env.getSource();
            DataLoader<String, Author> loader =
                    env.getDataLoader(DataLoaderRegistryFactory.AUTHOR_LOADER);
            return loader.load(comment.authorId());
        };
    }

    /** Comment.post — a single lookup (for demonstration; does not use a loader). */
    public DataFetcher<Post> commentPost() {
        return env -> {
            Comment comment = env.getSource();
            return store.findPost(comment.postId()).orElse(null);
        };
    }
}

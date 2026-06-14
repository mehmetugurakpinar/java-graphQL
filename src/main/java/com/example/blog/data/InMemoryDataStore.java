package com.example.blog.data;

import com.example.blog.domain.Author;
import com.example.blog.domain.Comment;
import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.domain.Role;
import com.example.blog.domain.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A simple store that keeps all data in memory. Instead of a real database it uses
 * Maps/Lists — so the focus stays on GraphQL concepts.
 *
 * <p>Teaching extras:</p>
 * <ul>
 *   <li>{@link #dbRoundTrips} — counts the number of "round trips" made to the data
 *       source. The DataLoader tests PROVE the difference between N+1 and batching
 *       using this counter.</li>
 *   <li>{@link #commentSink} — a reactive stream that publishes an event when a
 *       comment is added (the source of subscriptions).</li>
 * </ul>
 */
public class InMemoryDataStore {

    private final Map<String, Author> authors = new LinkedHashMap<>();
    private final Map<String, Post> posts = new LinkedHashMap<>();
    private final Map<String, Comment> comments = new LinkedHashMap<>();

    /** Counters for generating new ids. */
    private final AtomicLong postSeq = new AtomicLong(100);
    private final AtomicLong commentSeq = new AtomicLong(1000);

    /** Total number of queries (round trips) to the data source — for N+1 measurement. */
    private final AtomicInteger dbRoundTrips = new AtomicInteger(0);

    /**
     * A sink that multicasts comment events to many subscribers.
     * {@code multicast().onBackpressureBuffer()} lets multiple subscriptions listen
     * to the same stream.
     */
    private final Sinks.Many<Comment> commentSink = Sinks.many().multicast().onBackpressureBuffer();

    public InMemoryDataStore() {
        seed();
    }

    // ---------------------------------------------------------------------
    // Seed (initial data)
    // ---------------------------------------------------------------------
    private void seed() {
        putAuthor(new Author("a1", "Ada Lovelace", "ada@example.com", Role.ADMIN));
        putAuthor(new Author("a2", "Alan Turing", "alan@example.com", Role.AUTHOR));
        putAuthor(new Author("a3", "Grace Hopper", "grace@example.com", Role.AUTHOR));

        OffsetDateTime t = OffsetDateTime.parse("2026-01-01T09:00:00Z");
        putPost(new Post("p1", "Introduction to GraphQL", "GraphQL is a query language...",
                PostStatus.PUBLISHED, t, "a1", List.of("graphql", "api")));
        putPost(new Post("p2", "Differences from REST", "Over-fetching and under-fetching...",
                PostStatus.PUBLISHED, t.plusDays(1), "a1", List.of("graphql", "rest")));
        putPost(new Post("p3", "Solving N+1 with DataLoader", "How batch loading works...",
                PostStatus.PUBLISHED, t.plusDays(2), "a2", List.of("graphql", "performance")));
        putPost(new Post("p4", "Subscriptions", "Real-time data...",
                PostStatus.DRAFT, t.plusDays(3), "a3", List.of("graphql", "realtime")));

        putComment(new Comment("c1", "Great post!", t.plusHours(1), "a2", "p1"));
        putComment(new Comment("c2", "Very helpful.", t.plusHours(2), "a3", "p1"));
        putComment(new Comment("c3", "Thanks.", t.plusDays(1).plusHours(1), "a3", "p2"));
    }

    private void putAuthor(Author a) { authors.put(a.id(), a); }
    private void putPost(Post p) { posts.put(p.id(), p); }
    private void putComment(Comment c) { comments.put(c.id(), c); }

    // ---------------------------------------------------------------------
    // Single reads (each is 1 round trip)
    // ---------------------------------------------------------------------
    public Optional<Author> findAuthor(String id) {
        dbRoundTrips.incrementAndGet();
        return Optional.ofNullable(authors.get(id));
    }

    public Optional<Post> findPost(String id) {
        dbRoundTrips.incrementAndGet();
        return Optional.ofNullable(posts.get(id));
    }

    public List<Author> allAuthors() {
        dbRoundTrips.incrementAndGet();
        return new ArrayList<>(authors.values());
    }

    // ---------------------------------------------------------------------
    // BATCH reads — used by DataLoader: N records = 1 round trip
    // ---------------------------------------------------------------------
    public Map<String, Author> findAuthorsByIds(List<String> ids) {
        dbRoundTrips.incrementAndGet(); // a single round trip, no matter how many ids
        Map<String, Author> result = new LinkedHashMap<>();
        for (String id : ids) {
            Author a = authors.get(id);
            if (a != null) result.put(id, a);
        }
        return result;
    }

    /** Groups comments for the given post ids in a single pass. */
    public Map<String, List<Comment>> findCommentsByPostIds(List<String> postIds) {
        dbRoundTrips.incrementAndGet();
        Map<String, List<Comment>> grouped = postIds.stream()
                .collect(Collectors.toMap(id -> id, id -> new ArrayList<>(), (a, b) -> a, LinkedHashMap::new));
        for (Comment c : comments.values()) {
            if (grouped.containsKey(c.postId())) {
                grouped.get(c.postId()).add(c);
            }
        }
        return grouped;
    }

    /** Groups posts for the given author ids in a single pass. */
    public Map<String, List<Post>> findPostsByAuthorIds(List<String> authorIds) {
        dbRoundTrips.incrementAndGet();
        Map<String, List<Post>> grouped = authorIds.stream()
                .collect(Collectors.toMap(id -> id, id -> new ArrayList<>(), (a, b) -> a, LinkedHashMap::new));
        for (Post p : posts.values()) {
            if (grouped.containsKey(p.authorId())) {
                grouped.get(p.authorId()).add(p);
            }
        }
        return grouped;
    }

    // ---------------------------------------------------------------------
    // List / filter / pagination
    // ---------------------------------------------------------------------
    public List<Post> listPosts(PostStatus status, String authorId, String titleContains,
                                String tag, int limit, int offset) {
        dbRoundTrips.incrementAndGet();
        return posts.values().stream()
                .filter(p -> status == null || p.status() == status)
                .filter(p -> authorId == null || p.authorId().equals(authorId))
                .filter(p -> titleContains == null
                        || p.title().toLowerCase().contains(titleContains.toLowerCase()))
                .filter(p -> tag == null || p.tagNames().contains(tag))
                .skip(Math.max(0, offset))
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    public List<Comment> commentsForPost(String postId) {
        dbRoundTrips.incrementAndGet();
        return comments.values().stream()
                .filter(c -> c.postId().equals(postId))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // Search (UNION result): author name, post title, comment text
    // ---------------------------------------------------------------------
    public List<Object> search(String term) {
        dbRoundTrips.incrementAndGet();
        String q = term.toLowerCase();
        List<Object> results = new ArrayList<>();
        authors.values().stream()
                .filter(a -> a.name().toLowerCase().contains(q)).forEach(results::add);
        posts.values().stream()
                .filter(p -> p.title().toLowerCase().contains(q)).forEach(results::add);
        comments.values().stream()
                .filter(c -> c.text().toLowerCase().contains(q)).forEach(results::add);
        return results;
    }

    // ---------------------------------------------------------------------
    // Mutations (write)
    // ---------------------------------------------------------------------
    public Post createPost(String title, String content, String authorId,
                           PostStatus status, List<String> tagNames) {
        String id = "p" + postSeq.incrementAndGet();
        Post post = new Post(id, title, content,
                status == null ? PostStatus.DRAFT : status,
                OffsetDateTime.now(),
                authorId,
                tagNames == null ? List.of() : List.copyOf(tagNames));
        posts.put(id, post);
        return post;
    }

    public Post publishPost(String id) {
        Post existing = posts.get(id);
        if (existing == null) return null;
        Post published = new Post(existing.id(), existing.title(), existing.content(),
                PostStatus.PUBLISHED, existing.createdAt(), existing.authorId(), existing.tagNames());
        posts.put(id, published);
        return published;
    }

    /**
     * Adds a comment and publishes it through {@link #commentSink}. This publication
     * feeds the {@code commentAdded} subscription.
     */
    public Comment addComment(String postId, String text, String authorId) {
        String id = "c" + commentSeq.incrementAndGet();
        Comment comment = new Comment(id, text, OffsetDateTime.now(), authorId, postId);
        comments.put(id, comment);
        commentSink.tryEmitNext(comment); // publish the event to subscribers
        return comment;
    }

    // ---------------------------------------------------------------------
    // Subscription stream
    // ---------------------------------------------------------------------
    /** A stream that publishes the comments added to a specific post. */
    public Flux<Comment> commentStream(String postId) {
        return commentSink.asFlux().filter(c -> c.postId().equals(postId));
    }

    // ---------------------------------------------------------------------
    // Measurement helpers (for tests)
    // ---------------------------------------------------------------------
    public int dbRoundTrips() {
        return dbRoundTrips.get();
    }

    public void resetRoundTrips() {
        dbRoundTrips.set(0);
    }
}

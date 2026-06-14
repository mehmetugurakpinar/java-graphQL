package com.example.blog.graphql.fetchers;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.domain.Author;
import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.graphql.error.NotFoundException;
import graphql.schema.DataFetcher;

import java.util.List;
import java.util.Map;

/**
 * DataFetchers that resolve the fields of the {@code Query} root type.
 *
 * <p>Each DataFetcher receives a {@code DataFetchingEnvironment}; arguments are
 * accessed via {@code env.getArgument("...")}.</p>
 */
public class QueryFetchers {

    private final InMemoryDataStore store;

    public QueryFetchers(InMemoryDataStore store) {
        this.store = store;
    }

    /** post(id: ID!): Post — returns null if not found (the field is nullable). */
    public DataFetcher<Post> post() {
        return env -> store.findPost(env.getArgument("id")).orElse(null);
    }

    /** posts(filter, limit, offset): [Post!]! — argument + filter + pagination. */
    public DataFetcher<List<Post>> posts() {
        return env -> {
            Map<String, Object> filter = env.getArgument("filter");
            int limit = env.getArgumentOrDefault("limit", 10);
            int offset = env.getArgumentOrDefault("offset", 0);

            PostStatus status = null;
            String authorId = null;
            String titleContains = null;
            String tag = null;
            if (filter != null) {
                Object s = filter.get("status");
                status = s == null ? null : PostStatus.valueOf(s.toString());
                authorId = (String) filter.get("authorId");
                titleContains = (String) filter.get("titleContains");
                tag = (String) filter.get("tag");
            }
            return store.listPosts(status, authorId, titleContains, tag, limit, offset);
        };
    }

    /** author(id: ID!): Author — nullable. */
    public DataFetcher<Author> author() {
        return env -> store.findAuthor(env.getArgument("id")).orElse(null);
    }

    /** authors: [Author!]! */
    public DataFetcher<List<Author>> authors() {
        return env -> store.allAuthors();
    }

    /**
     * node(id: ID!): Node — an INTERFACE return. Finds one of Author/Post/Comment.
     * Throws NotFoundException if not found (an error-handling example).
     */
    public DataFetcher<Object> node() {
        return env -> {
            String id = env.getArgument("id");
            return store.findAuthor(id).map(a -> (Object) a)
                    .or(() -> store.findPost(id).map(p -> (Object) p))
                    .orElseThrow(() -> new NotFoundException("Node", id));
        };
    }

    /** search(term: String!): [SearchResult!]! — a UNION return. */
    public DataFetcher<List<Object>> search() {
        return env -> store.search(env.getArgument("term"));
    }
}

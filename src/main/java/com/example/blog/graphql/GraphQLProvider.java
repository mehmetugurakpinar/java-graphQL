package com.example.blog.graphql;

import com.example.blog.data.InMemoryDataStore;
import com.example.blog.domain.Author;
import com.example.blog.domain.Comment;
import com.example.blog.domain.Post;
import com.example.blog.domain.Tag;
import com.example.blog.graphql.dataloaders.DataLoaderRegistryFactory;
import com.example.blog.graphql.directive.UpperCaseDirective;
import com.example.blog.graphql.error.CustomExceptionHandler;
import com.example.blog.graphql.fetchers.FieldFetchers;
import com.example.blog.graphql.fetchers.MutationFetchers;
import com.example.blog.graphql.fetchers.QueryFetchers;
import com.example.blog.graphql.fetchers.SubscriptionFetchers;
import com.example.blog.graphql.instrument.LoggingInstrumentation;
import com.example.blog.graphql.scalar.DateTimeScalar;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * The central class that wires up the GraphQL engine by hand (without Spring).
 *
 * <p>Steps:</p>
 * <ol>
 *   <li>Read the schema.graphqls text and parse it into a {@link TypeDefinitionRegistry}.</li>
 *   <li>Use {@link RuntimeWiring} to bind each type/field to DataFetchers, register the
 *       custom scalar and directive, and provide a TypeResolver for the interface/union.</li>
 *   <li>Generate an executable {@link GraphQLSchema} with {@link SchemaGenerator}.</li>
 *   <li>Build the {@link GraphQL} instance with the custom exception handler + instrumentation.</li>
 * </ol>
 */
public class GraphQLProvider {

    private final GraphQL graphQL;
    private final InMemoryDataStore store;

    public GraphQLProvider(InMemoryDataStore store) {
        this(store, false);
    }

    /**
     * @param naiveAuthorResolution if true, Post.author is wired with the naive
     *                              (N+1-producing) resolver instead of the DataLoader —
     *                              for testing.
     */
    public GraphQLProvider(InMemoryDataStore store, boolean naiveAuthorResolution) {
        this.store = Objects.requireNonNull(store);
        this.graphQL = build(naiveAuthorResolution);
    }

    private GraphQL build(boolean naiveAuthorResolution) {
        String schemaText = readResource("schema.graphqls");

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaText);
        RuntimeWiring wiring = buildWiring(naiveAuthorResolution);
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);

        // The exception handler is passed to the query and mutation execution strategies.
        DataFetcherExceptionHandler exceptionHandler = new CustomExceptionHandler();

        return GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsyncExecutionStrategy(exceptionHandler))
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(exceptionHandler))
                .instrumentation(new LoggingInstrumentation())
                .build();
    }

    private RuntimeWiring buildWiring(boolean naiveAuthorResolution) {
        QueryFetchers query = new QueryFetchers(store);
        MutationFetchers mutation = new MutationFetchers(store);
        SubscriptionFetchers subscription = new SubscriptionFetchers(store);
        FieldFetchers field = new FieldFetchers(store);

        // Resolver that determines the concrete type for the interface (Node) and union (SearchResult).
        TypeResolver typeResolver = env -> {
            Object o = env.getObject();
            String typeName;
            if (o instanceof Author) typeName = "Author";
            else if (o instanceof Post) typeName = "Post";
            else if (o instanceof Comment) typeName = "Comment";
            else if (o instanceof Tag) typeName = "Tag";
            else throw new IllegalStateException("Unknown type: " + o);
            return env.getSchema().getObjectType(typeName);
        };

        return RuntimeWiring.newRuntimeWiring()
                // custom scalar
                .scalar(DateTimeScalar.INSTANCE)
                // custom directive
                .directive("uppercase", new UpperCaseDirective())
                // root types
                .type(newTypeWiring("Query")
                        .dataFetcher("post", query.post())
                        .dataFetcher("posts", query.posts())
                        .dataFetcher("author", query.author())
                        .dataFetcher("authors", query.authors())
                        .dataFetcher("node", query.node())
                        .dataFetcher("search", query.search()))
                .type(newTypeWiring("Mutation")
                        .dataFetcher("createPost", mutation.createPost())
                        .dataFetcher("publishPost", mutation.publishPost())
                        .dataFetcher("addComment", mutation.addComment()))
                .type(newTypeWiring("Subscription")
                        .dataFetcher("commentAdded", subscription.commentAdded()))
                // related fields
                .type(newTypeWiring("Post")
                        .dataFetcher("author",
                                naiveAuthorResolution ? field.postAuthorNaive() : field.postAuthor())
                        .dataFetcher("comments", field.postComments())
                        .dataFetcher("tags", field.postTags())
                        .dataFetcher("shoutTitle", field.postShoutTitle())
                        .dataFetcher("summary", field.postSummary()))
                .type(newTypeWiring("Author")
                        .dataFetcher("posts", field.authorPosts()))
                .type(newTypeWiring("Comment")
                        .dataFetcher("author", field.commentAuthor())
                        .dataFetcher("post", field.commentPost()))
                // interface + union resolvers
                .type(newTypeWiring("Node").typeResolver(typeResolver))
                .type(newTypeWiring("SearchResult").typeResolver(typeResolver))
                .build();
    }

    // ------------------------------------------------------------------
    // Execution helpers
    // ------------------------------------------------------------------

    public ExecutionResult execute(String query) {
        return execute(query, Map.of(), null);
    }

    public ExecutionResult execute(String query, Map<String, Object> variables, String operationName) {
        return graphQL.execute(buildInput(query, variables, operationName));
    }

    /**
     * Builds the ExecutionInput. For every request:
     * <ul>
     *   <li>a NEW DataLoaderRegistry (per-request cache),</li>
     *   <li>a fake logged-in user ("currentUserId") placed into the GraphQLContext.</li>
     * </ul>
     */
    public ExecutionInput buildInput(String query, Map<String, Object> variables, String operationName) {
        return ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .variables(variables == null ? Map.of() : variables)
                .graphQLContext(Map.of("currentUserId", "a1"))
                .dataLoaderRegistry(DataLoaderRegistryFactory.create(store))
                .build();
    }

    public GraphQL getGraphQL() {
        return graphQL;
    }

    public InMemoryDataStore getStore() {
        return store;
    }

    private static String readResource(String name) {
        try (InputStream in = GraphQLProvider.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read resource: " + name, e);
        }
    }
}

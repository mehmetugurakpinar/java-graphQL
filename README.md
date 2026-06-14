# GraphQL in the Spotlight — A Learning Project with Pure `graphql-java`

This project is designed to teach **every detail of GraphQL** hands-on. It uses no
GraphQL framework (Spring for GraphQL, DGS, etc.) — the engine is wired **by hand**
directly with [`graphql-java`](https://www.graphql-java.com/). This way you see every
piece in its raw form: schema parsing, runtime wiring, DataFetchers, scalars/directives,
DataLoader and subscriptions.

Domain: a small **blog** (Author · Post · Comment · Tag). Data is kept in memory
(no database), so the focus stays entirely on GraphQL.

There are also classic **REST endpoints** serving the same data, so you can concretely
compare the differences with GraphQL (over-fetching, under-fetching).

---

## Quick Start

```bash
# Run the tests (each test proves a GraphQL concept)
mvn test

# Start the server
mvn compile exec:java
#   or as a single jar:
mvn package && java -jar target/java-graphql-blog.jar
```

The server runs on `http://localhost:7070`:

| Endpoint | Description |
|---|---|
| `GET  /graphiql` | GraphiQL UI in the browser (try query/mutation/subscription) |
| `POST /graphql` | GraphQL over HTTP — query + mutation |
| `WS   /subscriptions` | Subscriptions (graphql-ws protocol) |
| `GET  /api/...` | REST comparison endpoints |

---

## What Will You Learn by Exploring the Project? (Concept Map)

For each concept, you can find **where it is defined** and **which test proves it**.
To learn: read the relevant section of `schema.graphqls` first, then the fetcher,
then the test.

| GraphQL Concept | Where | Test |
|---|---|---|
| Schema (schema-first) | `src/main/resources/schema.graphqls` | — |
| Built-in + custom **scalar** (`DateTime`) | `graphql/scalar/DateTimeScalar.java` | `DirectiveScalarTest` |
| **Enum** (`PostStatus`, `Role`) | schema + `domain/` | `QueryTest`, `MutationTest` |
| **Object type** + field selection | schema + `fetchers/` | `QueryTest` |
| **Interface** (`Node`) + inline fragment | schema + `GraphQLProvider` (typeResolver) | `InterfaceUnionTest` |
| **Union** (`SearchResult`) | schema + `QueryFetchers.search` | `InterfaceUnionTest` |
| **Input type** (`CreatePostInput` …) | schema + `MutationFetchers` | `MutationTest` |
| **Arguments**, default values, **pagination** | schema `Query.posts` + `QueryFetchers.posts` | `QueryTest` |
| **Query** | `fetchers/QueryFetchers.java` | `QueryTest` |
| **Mutation** | `fetchers/MutationFetchers.java` | `MutationTest` |
| **Subscription** (WebSocket) | `fetchers/SubscriptionFetchers.java`, `transport/GraphQLWsHandler.java` | `SubscriptionTest` |
| **Alias** | — | `QueryTest.aliases` |
| **Fragment / inline fragment** | — | `QueryTest`, `InterfaceUnionTest` |
| **Variables** | — | `QueryTest.variables`, `MutationTest` |
| Built-in **directives** `@skip`/`@include`/`@deprecated` | schema | `DirectiveScalarTest`, `IntrospectionTest` |
| Custom **directive** `@uppercase` | `graphql/directive/UpperCaseDirective.java` | `DirectiveScalarTest` |
| Related fields (**nested resolver**) | `fetchers/FieldFetchers.java` | `QueryTest.nestedGraphTraversal` |
| **DataLoader** & **N+1** solution | `graphql/dataloaders/` + `FieldFetchers` | `DataLoaderTest` |
| **GraphQLContext** (current user) | `GraphQLProvider.buildInput` + `MutationFetchers.addComment` | `MutationTest` |
| **Error handling** (errors[], path, extensions, partial result) | `graphql/error/` | `ErrorHandlingTest` |
| **Introspection** (`__schema`, `__type`) | (provided by the engine) | `IntrospectionTest` |
| **Instrumentation** | `graphql/instrument/LoggingInstrumentation.java` | (visible in logs) |

---

## Project Structure

```
src/main/java/com/example/blog/
  App.java                     # main: GraphQL + Javalin setup
  domain/                      # Author, Post, Comment, Tag, enums (records)
  data/InMemoryDataStore.java  # seed data, queries/mutations, round-trip counter, event stream
  graphql/
    GraphQLProvider.java       # schema + RuntimeWiring + GraphQL instance (the heart)
    scalar/DateTimeScalar.java
    directive/UpperCaseDirective.java
    fetchers/                  # Query/Mutation/Subscription/Field DataFetchers
    dataloaders/               # batch loaders (N+1 solution)
    error/                     # NotFoundException + DataFetcherExceptionHandler
    instrument/                # LoggingInstrumentation
  transport/                   # HTTP (POST /graphql) + WebSocket (subscriptions)
  rest/RestController.java     # REST comparison endpoints
src/main/resources/
  schema.graphqls              # the ENTIRE type system, heavily commented
  graphiql.html                # browser UI
src/test/java/com/example/blog/ # a separate test class per concept
```

`GraphQLProvider` is the heart of the project: it reads the schema, binds each type/field
to DataFetchers, registers the custom scalar + directive + interface/union resolvers, and
builds the `GraphQL` instance with an exception handler + instrumentation.

---

## REST vs GraphQL — A Concrete Comparison

While the server is running, try the commands below.

### 1) Over-fetching (REST sends more than you need)

**REST** — even if you only want the title, you get everything:

```bash
curl -s http://localhost:7070/api/posts/p1
# { "id":"p1", "title":"...", "content":"...", "status":"PUBLISHED",
#   "createdAt":"...", "authorId":"a1", "tagNames":[...] }   <-- everything came back
```

**GraphQL** — only the fields you request are returned:

```bash
curl -s http://localhost:7070/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ post(id:\"p1\"){ title } }"}'
# { "data": { "post": { "title": "Introduction to GraphQL" } } }    <-- title only
```

### 2) Under-fetching / N round-trips (REST needs many requests)

To display a post together with its **author and comments**:

**REST → 3 requests:**

```bash
curl -s http://localhost:7070/api/posts/p1            # 1) the post (learn authorId)
curl -s http://localhost:7070/api/authors/a1          # 2) the author
curl -s http://localhost:7070/api/posts/p1/comments   # 3) the comments
```

**GraphQL → 1 request:**

```bash
curl -s http://localhost:7070/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ post(id:\"p1\"){ title author{ name } comments{ text } } }"}'
```

> This difference is also proven with real HTTP requests inside `RestComparisonTest`.

---

## DataLoader & the N+1 Problem

Consider the query `{ posts { author { name } } }`: 4 posts + the author of each.

- **Naive approach:** 1 (list) + 4 (one author per post) = **5 queries** → N+1.
- **DataLoader:** collects all author ids and resolves them in a **single batch** = **2 queries**.

`InMemoryDataStore` counts one "round trip" per data-source access. `DataLoaderTest`
runs both paths and proves the difference by comparing the counts (**5 vs 2**):

```
batchedResolutionAvoidsNPlusOne  -> dbRoundTrips == 2
naiveResolutionCausesNPlusOne    -> dbRoundTrips == 5
```

Relevant files: `graphql/dataloaders/DataLoaderRegistryFactory.java`,
`graphql/fetchers/FieldFetchers.java` (`postAuthor` vs `postAuthorNaive`).

---

## Subscriptions (Real-Time)

When the `addComment` mutation adds a comment, `InMemoryDataStore` publishes an event
through a reactor `Sinks`. The `commentAdded(postId)` subscription filters that stream
and delivers it to the client.

Try it by opening two tabs in GraphiQL:

```graphql
# Tab 1 — listen
subscription { commentAdded(postId: "p1") { id text author { name } } }

# Tab 2 — trigger
mutation { addComment(input: { postId: "p1", text: "Hello!" }) { id } }
```

The same behavior is tested at the engine level with `StepVerifier` in `SubscriptionTest`.

---

## Notes

- **Pure graphql-java:** All wiring is done by hand; there is no Spring automation. That is the point.
- **Javalin** and **reactor-core** are only transport/stream libraries; they do not
  abstract GraphQL — the GraphQL logic is entirely under your control.
- All code comments are written in English with a concept-by-concept teaching tone.

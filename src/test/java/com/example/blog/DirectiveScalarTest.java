package com.example.blog;

import com.example.blog.graphql.GraphQLProvider;
import com.example.blog.graphql.scalar.DateTimeScalar;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DIRECTIVE (@uppercase, @skip/@include) and custom SCALAR (DateTime) behaviors.
 */
class DirectiveScalarTest {

    private GraphQLProvider provider;

    @BeforeEach
    void setUp() {
        provider = TestSupport.newProvider();
    }

    @Test
    @DisplayName("The custom @uppercase directive upper-cases the field")
    void uppercaseDirective() {
        ExecutionResult result = provider.execute("{ post(id: \"p1\") { title shoutTitle } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        String title = (String) post.get("title");
        assertThat(post.get("shoutTitle")).isEqualTo(title.toUpperCase());
    }

    @Test
    @DisplayName("The built-in @skip directive excludes a field")
    void skipDirective() {
        String query = "query($skip: Boolean!){ post(id: \"p1\") { title @skip(if: $skip) status } }";
        ExecutionResult result = provider.execute(query, Map.of("skip", true), null);

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        assertThat(post).doesNotContainKey("title"); // skip(if:true) -> field absent
        assertThat(post).containsKey("status");
    }

    @Test
    @DisplayName("The built-in @include directive conditionally includes a field")
    void includeDirective() {
        String query = "query($inc: Boolean!){ post(id: \"p1\") { title @include(if: $inc) } }";
        ExecutionResult result = provider.execute(query, Map.of("inc", false), null);

        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        assertThat(post).doesNotContainKey("title"); // include(if:false) -> field absent
    }

    @Test
    @DisplayName("The custom DateTime scalar output is an ISO-8601 String")
    void dateTimeScalarSerialize() {
        ExecutionResult result = provider.execute("{ post(id: \"p1\") { createdAt } }");

        Map<String, Object> post = cast(((Map<String, Object>) result.getData()).get("post"));
        assertThat(post.get("createdAt")).isEqualTo("2026-01-01T09:00:00Z");
    }

    @Test
    @DisplayName("DateTime scalar parseValue: String -> OffsetDateTime (input direction)")
    void dateTimeScalarParseValue() {
        Object parsed = DateTimeScalar.INSTANCE.getCoercing()
                .parseValue("2026-06-14T10:15:30Z", GraphQLContext.getDefault(), Locale.ROOT);
        assertThat(parsed).isInstanceOf(OffsetDateTime.class);
        assertThat(parsed).isEqualTo(OffsetDateTime.parse("2026-06-14T10:15:30Z"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}

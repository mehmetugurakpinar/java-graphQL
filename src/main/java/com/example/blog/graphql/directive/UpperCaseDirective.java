package com.example.blog.graphql.directive;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;

/**
 * The behavior of the {@code @uppercase} directive.
 *
 * <p>A directive works by wrapping the DataFetcher of the field (FIELD_DEFINITION)
 * it is placed on. Here we build a wrapper that takes the original fetcher's String
 * result and upper-cases it. Thanks to this, the schema's
 * {@code shoutTitle: String! @uppercase} field returns the title in UPPER CASE.</p>
 *
 * <p>This is a powerful technique called "schema transformation": you can change a
 * field's behavior declaratively from the schema.</p>
 */
public class UpperCaseDirective implements SchemaDirectiveWiring {

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {
        GraphQLFieldDefinition field = env.getElement();

        // Wrap the field's current (original) DataFetcher with a new DataFetcher
        // that upper-cases the result.
        DataFetcher<?> wrapped = DataFetcherFactories.wrapDataFetcher(
                env.getFieldDataFetcher(),
                (dfEnv, value) -> value instanceof String s ? s.toUpperCase() : value
        );

        env.setFieldDataFetcher(wrapped);
        return field;
    }
}

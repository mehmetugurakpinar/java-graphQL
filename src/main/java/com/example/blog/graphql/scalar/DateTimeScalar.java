package com.example.blog.graphql.scalar;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * A custom scalar type: {@code DateTime}.
 *
 * <p>A scalar has three behaviors:</p>
 * <ul>
 *   <li><b>serialize</b>: Java object -> the JSON value sent to the client (output).</li>
 *   <li><b>parseValue</b>: JSON arriving via variables -> Java object (input).</li>
 *   <li><b>parseLiteral</b>: a value EMBEDDED in the query text (e.g. a "2026-..." literal)
 *       -> Java object.</li>
 * </ul>
 *
 * <p>Here we convert between {@link OffsetDateTime} and ISO-8601 text.</p>
 */
public final class DateTimeScalar {

    private DateTimeScalar() {
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("ISO-8601 date/time scalar")
            .coercing(new Coercing<OffsetDateTime, String>() {

                // OUTPUT: server-side OffsetDateTime -> String sent to the client
                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext ctx, Locale locale)
                        throws CoercingSerializeException {
                    if (dataFetcherResult instanceof OffsetDateTime odt) {
                        return FMT.format(odt);
                    }
                    throw new CoercingSerializeException(
                            "Could not serialize DateTime: " + dataFetcherResult);
                }

                // INPUT (variables): JSON String -> OffsetDateTime
                @Override
                public OffsetDateTime parseValue(Object input, GraphQLContext ctx, Locale locale)
                        throws CoercingParseValueException {
                    try {
                        return OffsetDateTime.parse(String.valueOf(input), FMT);
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseValueException(
                                "Invalid DateTime value: " + input, e);
                    }
                }

                // INPUT (literal): a value embedded in the query -> OffsetDateTime
                @Override
                public OffsetDateTime parseLiteral(Value<?> input, CoercedVariables variables,
                                                   GraphQLContext ctx, Locale locale)
                        throws CoercingParseLiteralException {
                    if (input instanceof StringValue sv) {
                        try {
                            return OffsetDateTime.parse(sv.getValue(), FMT);
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseLiteralException(
                                    "Invalid DateTime literal: " + sv.getValue(), e);
                        }
                    }
                    throw new CoercingParseLiteralException(
                            "DateTime must be a String literal");
                }
            })
            .build();
}

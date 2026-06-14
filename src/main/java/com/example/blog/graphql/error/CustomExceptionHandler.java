package com.example.blog.graphql.error;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Converts exceptions thrown inside a DataFetcher into GraphQL errors.
 *
 * <p>Error handling in GraphQL differs from REST: instead of a single HTTP status
 * code, each error is returned in the response's {@code "errors"} array with a
 * {@code message}, {@code path} (where it occurred), {@code locations} (position in
 * the query) and {@code extensions} (custom extra data). Moreover, other fields can
 * still return successfully — this is called a "partial result".</p>
 */
public class CustomExceptionHandler implements DataFetcherExceptionHandler {

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters params) {

        Throwable ex = params.getException();

        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError()
                .message(ex.getMessage())
                .location(params.getSourceLocation())
                .path(params.getPath());

        if (ex instanceof NotFoundException nf) {
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", "NOT_FOUND");
            extensions.put("entity", nf.entity());
            extensions.put("entityId", nf.entityId());
            builder.errorType(ErrorType.DataFetchingException).extensions(extensions);
        } else {
            builder.errorType(ErrorType.DataFetchingException);
        }

        GraphQLError error = builder.build();
        DataFetcherExceptionHandlerResult result = DataFetcherExceptionHandlerResult.newResult()
                .error(error)
                .build();
        return CompletableFuture.completedFuture(result);
    }
}

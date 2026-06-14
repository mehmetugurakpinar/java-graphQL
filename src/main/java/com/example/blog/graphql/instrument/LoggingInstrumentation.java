package com.example.blog.graphql.instrument;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple "instrumentation" example.
 *
 * <p>Instrumentation lets you hook into the lifecycle of GraphQL execution (operation
 * start, field resolution, result). It is used for cross-cutting concerns such as
 * tracing, metrics, logging and authorization.</p>
 *
 * <p>Here we log the total duration of each operation. Observing how the duration
 * changes while many fields are resolved (e.g. an N+1 scenario) is instructive.</p>
 */
public class LoggingInstrumentation extends SimplePerformantInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(LoggingInstrumentation.class);

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters, InstrumentationState state) {

        long start = System.nanoTime();
        String operation = parameters.getOperation() != null ? parameters.getOperation() : "<anonymous>";

        // whenCompleted: called when execution finishes
        return SimpleInstrumentationContext.whenCompleted((result, throwable) -> {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("GraphQL operation '{}' took {} ms", operation, ms);
        });
    }
}

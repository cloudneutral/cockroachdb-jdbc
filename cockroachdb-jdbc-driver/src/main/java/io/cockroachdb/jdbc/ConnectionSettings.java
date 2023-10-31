package io.cockroachdb.jdbc;

import java.util.Optional;

import io.cockroachdb.jdbc.rewrite.sfu.QueryProcessor;
import io.cockroachdb.jdbc.retry.MethodTraceLogger;
import io.cockroachdb.jdbc.retry.RetryListener;
import io.cockroachdb.jdbc.retry.RetryStrategy;

/**
 * Value object for JDBC connection settings.
 */
@SuppressWarnings("UnusedReturnValue")
public class ConnectionSettings {
    private boolean useCockroachMetadata;

    private QueryProcessor queryProcessor;

    private RetryStrategy retryStrategy;

    private RetryListener retryListener;

    private MethodTraceLogger methodTraceLogger;

    public MethodTraceLogger getMethodTraceLogger() {
        return methodTraceLogger;
    }

    public void setMethodTraceLogger(MethodTraceLogger methodTraceLogger) {
        this.methodTraceLogger = methodTraceLogger;
    }

    public boolean isUseCockroachMetadata() {
        return useCockroachMetadata;
    }

    public ConnectionSettings setUseCockroachMetadata(boolean useCockroachMetadata) {
        this.useCockroachMetadata = useCockroachMetadata;
        return this;
    }

    public QueryProcessor getQueryProcessor() {
        return Optional.of(queryProcessor).get(); // NPE here if not set
    }

    public ConnectionSettings setQueryProcessor(QueryProcessor queryProcessor) {
        this.queryProcessor = queryProcessor;
        return this;
    }

    public RetryStrategy getRetryStrategy() {
        return Optional.of(retryStrategy).get(); // NPE here if not set
    }

    public ConnectionSettings setRetryStrategy(RetryStrategy retryStrategy) {
        this.retryStrategy = retryStrategy;
        return this;
    }

    public RetryListener getRetryListener() {
        return Optional.of(retryListener).get(); // NPE here if not set
    }

    public ConnectionSettings setRetryListener(RetryListener retryListener) {
        this.retryListener = retryListener;
        return this;
    }
}

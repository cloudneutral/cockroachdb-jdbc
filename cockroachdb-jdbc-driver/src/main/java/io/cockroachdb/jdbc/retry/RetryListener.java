package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

/**
 * Interface specifying the API to be implemented by a class providing
 * a retry listener.
 *
 * <p>This is intended for internal use by the CockroachDB JDBC driver.
 * See {@link LoggingRetryListener}.
 */
@SuppressWarnings("EmptyMethod")
public interface RetryListener {
    /**
     * Configure the listener, if supported.
     *
     * @param properties the configuration properties, optionally provided in JDBC URL
     */
    void configure(Properties properties);

    /**
     * Invoked at the beginning of a transaction retry attempt.
     *
     * @param methodName the original JDBC method that threw a retryable exception
     * @param attempt the current attempt number, 1-based
     * @param ex the original retryable SQL exception
     * @param backoffDelay backoff duration before the retry
     */
    default void beforeRetry(String methodName, int attempt, SQLException ex, Duration backoffDelay) {
    }

    /**
     * Invoked at the end of a transaction retry attempt, regardless whether it succeeded or failed.
     *
     * @param methodName the original JDBC method that threw a retryable exception
     * @param attempt the current attempt number, 1-based
     * @param ex a new SQL exception from the most recent retry attempt, or null if the retry was successful
     * @param executionTime total time spent in retry attempts
     */
    default void afterRetry(String methodName, int attempt, SQLException ex, Duration executionTime) {
    }
}

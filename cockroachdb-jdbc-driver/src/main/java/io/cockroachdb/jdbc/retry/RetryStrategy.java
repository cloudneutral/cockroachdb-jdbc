package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

/**
 * Interface specifying the API to be implemented by a class providing
 * a retry strategy in terms of exception classification.
 *
 * <p>This is intended for internal use by the CockroachDB JDBC driver.
 * See {@link ExponentialBackoffRetryStrategy}.
 *
 * @author Kai Niemi
 */
public interface RetryStrategy {
    /**
     * Configure the strategy, implementation specific.
     *
     * @param properties the configuration properties, optionally provided in JDBC URL
     */
    default void configure(Properties properties) {
    }

    default String getDescription() {
        return toString();
    }

    /**
     * Classification of exceptions as connection failures or not. Connection errors are
     * potentially recoverable and a retry may be permitted or not depending on strategy.
     *
     * @param ex the exception to inspect
     * @return true if the exception denotes a connection error and false if not
     */
    boolean isConnectionError(SQLException ex);

    /**
     * Classification of exceptions as retryable or not. Typically, the supplied
     * exception is an optimistic or pessimistic lock exception or some other
     * data access exception considered transient - where a previously failed
     * operation might succeed when the operation is retried without any intervention
     * by application-level functionality.
     *
     * @param ex the exception to inspect
     * @return true if the exception is retryable and false if not
     */
    boolean isRetryableException(SQLException ex);

    /**
     * Determine if a retry attempt should proceed or not.
     *
     * @param attempt the retry attempt number, 1-based
     * @return true to proceed, false to cancel
     */
    boolean proceedWithRetry(int attempt);

    /**
     * Determine if a retry attempt should proceed or not.
     *
     * @param attempt the retry attempt number, 1-based
     * @return backoff duration
     */
    Duration getBackoffDuration(int attempt);
}

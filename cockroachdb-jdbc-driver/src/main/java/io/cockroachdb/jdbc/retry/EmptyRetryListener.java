package io.cockroachdb.jdbc.retry;

import java.util.Properties;

/**
 * A no-op retry listener.
 */
public class EmptyRetryListener implements RetryListener {
    @Override
    public void configure(Properties properties) {
    }
}

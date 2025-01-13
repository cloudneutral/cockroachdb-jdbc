package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import io.cockroachdb.jdbc.CockroachProperty;
import io.cockroachdb.jdbc.util.Assert;
import io.cockroachdb.jdbc.util.ExceptionUtils;

/**
 * Retry listener delegating to a logger.
 *
 * @author Kai Niemi
 */
public class LoggingRetryListener implements RetryListener {
    private final AtomicInteger totalSuccess = new AtomicInteger();

    private final AtomicInteger totalFailures = new AtomicInteger();

    private final Logger logger;

    private final Marker marker = MarkerFactory.getMarker("RETRY");

    private int maxAttempts;

    public LoggingRetryListener() {
        this(LoggerFactory.getLogger(LoggingRetryListener.class));
    }

    public LoggingRetryListener(Logger logger) {
        this.logger = logger;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        Assert.isTrue(maxAttempts > 0, "maxAttempts must be > 0");
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void configure(Properties properties) {
        setMaxAttempts(
                Integer.parseInt(CockroachProperty.RETRY_MAX_ATTEMPTS.toDriverPropertyInfo(properties).value));
    }

    @Override
    public void beforeRetry(String methodName, int attempt, SQLException ex, Duration backoffDelay) {
        logger.info(marker,
                "Transaction retry started: attempt [{}/{}] for method [{}] backoff delay [{}]\n{}",
                attempt, maxAttempts, methodName, backoffDelay, ExceptionUtils.toNestedString(ex));
    }

    @Override
    public void afterRetry(String methodName, int attempt, SQLException ex, Duration executionTime) {
        if (ex != null) {
            totalFailures.incrementAndGet();
            logger.warn(marker,
                    "Transaction retry failed: attempt [{}/{}] for method [{}] wait time [{}]. Total [{}] successful [{}] failed\n{}",
                    attempt, maxAttempts, methodName, executionTime,
                    totalSuccess.get(), totalFailures.get(),
                    ExceptionUtils.toNestedString(ex));
        } else {
            totalSuccess.incrementAndGet();
            logger.info(marker,
                    "Transaction retry successful: attempt [{}/{}] for method [{}] wait time [{}]. Total [{}] successful [{}] failed",
                    attempt, maxAttempts, methodName, executionTime,
                    totalSuccess.get(), totalFailures.get());
        }
    }

    public int getTotalSuccessfulRetries() {
        return totalSuccess.get();
    }

    public int getTotalFailedRetries() {
        return totalFailures.get();
    }
}


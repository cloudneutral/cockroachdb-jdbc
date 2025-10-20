package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import org.postgresql.util.PSQLState;

import io.cockroachdb.jdbc.CockroachProperty;
import io.cockroachdb.jdbc.util.Assert;
import io.cockroachdb.jdbc.util.DurationFormat;

/**
 * Default implementation of a retry strategy using exponential backoff with jitter.
 *
 * @author Kai Niemi
 */
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    public static final Duration MAX_BACKOFF_TIME = Duration.ofSeconds(30);

    public static final int MAX_ATTEMPTS = 15;

    private boolean retryConnectionErrors;

    private double multiplier = 2.0;

    private int maxAttempts = MAX_ATTEMPTS;

    private Duration maxBackoffTime = MAX_BACKOFF_TIME;

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        Assert.isTrue(multiplier > 0, "multiplier must be > 0");
        this.multiplier = multiplier;
    }

    public boolean isRetryConnectionErrors() {
        return retryConnectionErrors;
    }

    public void setRetryConnectionErrors(boolean retryConnectionErrors) {
        this.retryConnectionErrors = retryConnectionErrors;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        Assert.isTrue(maxAttempts > 0, "maxAttempts must be > 0");
        this.maxAttempts = maxAttempts;
    }

    public Duration getMaxBackoffTime() {
        return maxBackoffTime;
    }

    public void setMaxBackoffTime(Duration maxBackoffTime) {
        Assert.isTrue(!maxBackoffTime.isNegative(), "maxBackoffTime must be >= 0");
        this.maxBackoffTime = maxBackoffTime;
    }

    @Override
    public void configure(Properties properties) {
        setRetryConnectionErrors(
                Boolean.parseBoolean(CockroachProperty.RETRY_CONNECTION_ERRORS.toDriverPropertyInfo(properties).value));
        setMaxAttempts(
                Integer.parseInt(CockroachProperty.RETRY_MAX_ATTEMPTS.toDriverPropertyInfo(properties).value));
        setMaxBackoffTime(
                DurationFormat.parseDuration(
                        CockroachProperty.RETRY_MAX_BACKOFF_TIME.toDriverPropertyInfo(properties).value));
    }

    @Override
    public boolean isConnectionError(SQLException ex) {
        String sqlState = ex.getSQLState();
        return PSQLState.isConnectionError(sqlState)
                || PSQLState.COMMUNICATION_ERROR.getState().equals(sqlState)
                // Indicates that the server is not accepting new transactions on existing connections.
                || "57P01".equals(sqlState);
    }

    @Override
    public boolean isRetryableException(SQLException ex) {
        if (isRetryConnectionErrors() && isConnectionError(ex)) {
            return true;
        }
        return PSQLState.SERIALIZATION_FAILURE.getState().equals(ex.getSQLState());
    }

    @Override
    public boolean proceedWithRetry(int attempt) {
        return attempt <= maxAttempts;
    }

    @Override
    public Duration getBackoffDuration(int attempt) {
        return backoffInterval(attempt);
    }

    /**
     * Calculate backoff interval in millis using exponential multiplier and jitter.
     *
     * @param attempt the current attempt, 1-based
     * @return backoff interval
     */
    public Duration backoffInterval(int attempt) {
        double expBackoff = Math.pow(multiplier, attempt) + 100;
        return Duration.ofMillis(Math.min((long) expBackoff + ThreadLocalRandom.current().nextLong(1000),
                maxBackoffTime.toMillis()));
    }

    @Override
    public String toString() {
        return "ExponentialBackoffRetryStrategy{" +
                "retryConnectionErrors=" + retryConnectionErrors +
                ", multiplier=" + multiplier +
                ", maxAttempts=" + maxAttempts +
                ", maxBackoffTime=" + maxBackoffTime +
                '}';
    }
}

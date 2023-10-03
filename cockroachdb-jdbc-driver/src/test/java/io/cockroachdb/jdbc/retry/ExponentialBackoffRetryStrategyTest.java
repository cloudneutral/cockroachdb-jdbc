package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.CockroachProperty;

@Tag("unit-test")
public class ExponentialBackoffRetryStrategyTest {
    @Test
    public void testPrintIntervals() {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMultiplier(2);
        strategy.setMaxBackoffTime(Duration.ofSeconds(15));

        IntStream.rangeClosed(1, 10).forEach(value -> {
            System.out.println(strategy.backoffInterval(value));
        });
    }

    @Test
    public void whenConfiguring_expectCorrectValues() {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();

        Assertions.assertEquals(ExponentialBackoffRetryStrategy.MAX_ATTEMPTS, strategy.getMaxAttempts());
        Assertions.assertEquals(ExponentialBackoffRetryStrategy.MAX_BACKOFF_TIME, strategy.getMaxBackoffTime());

        Properties props = new Properties();
        props.setProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "3");
        props.setProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "5000");
        strategy.configure(props);

        Assertions.assertEquals(3, strategy.getMaxAttempts());
        Assertions.assertEquals(Duration.ofSeconds(5), strategy.getMaxBackoffTime());

        strategy.setMaxAttempts(4);
        strategy.setMaxBackoffTime(Duration.ofSeconds(8));

        Assertions.assertEquals(4, strategy.getMaxAttempts());
        Assertions.assertEquals(Duration.ofSeconds(8), strategy.getMaxBackoffTime());
    }

    @Test
    public void whenClassifyingSerializationException_expectAccepted() {
        RetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        Assertions.assertTrue(strategy.isRetryableException(new SQLException("Disturbance", "40001")));
        Assertions.assertFalse(strategy.isRetryableException(new SQLException("Disturbance", "40003")));
    }

    @Test
    public void whenProceedWithRetry_expectAttemptsAndExpiryTimeEffect() {
        Properties props = new Properties();
        props.setProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "3");
        props.setProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "5s");

        RetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.configure(props);

        Assertions.assertFalse(strategy.getBackoffDuration(1).isZero());
        Assertions.assertFalse(strategy.getBackoffDuration(2).isZero());
        Assertions.assertFalse(strategy.getBackoffDuration(3).isZero());
    }
}

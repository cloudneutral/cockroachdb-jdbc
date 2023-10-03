package io.cockroachdb.jdbc.util;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DurationFormatTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    public void testInstant() {
        Instant a = Instant.now();
        Instant b = Instant.now().plus(Duration.ofMillis(123)).plus(Duration.ofNanos(456));
        logger.info("{}", Duration.between(a, b));
        logger.info("{}", Duration.between(b, a));
    }

    @Test
    public void testDurationExpressions() {
        Assertions.assertEquals(Duration.ofMillis(150), DurationFormat.parseDuration("150ms"));
        Assertions.assertEquals(Duration.ofMinutes(2).plus(Duration.ofSeconds(3).plus(Duration.ofMillis(125))),
                DurationFormat.parseDuration("2m 3s 125ms"));
        Assertions.assertEquals(Duration.ofMillis(0), DurationFormat.parseDuration("0"));
        Assertions.assertEquals(Duration.ofMillis(0), DurationFormat.parseDuration("0s"));
        Assertions.assertEquals(Duration.ofMillis(30), DurationFormat.parseDuration("30"));
        Assertions.assertEquals(Duration.ofSeconds(30), DurationFormat.parseDuration("30s"));
        Assertions.assertEquals(Duration.ofMinutes(30), DurationFormat.parseDuration("30m"));
        Assertions.assertEquals(Duration.ofHours(30), DurationFormat.parseDuration("30h"));
        Assertions.assertEquals(Duration.ofDays(30), DurationFormat.parseDuration("30d"));
        Assertions.assertEquals(Duration.ofMinutes(10).plus(Duration.ofSeconds(30)),
                DurationFormat.parseDuration("10m30s"));
        Assertions.assertEquals(Duration.ofHours(10).plus(Duration.ofMinutes(3).plus(Duration.ofSeconds(15))),
                DurationFormat.parseDuration("10h3m15s"));
        Assertions.assertEquals(Duration.ofHours(10).plus(Duration.ofMinutes(3).plus(Duration.ofSeconds(15))),
                DurationFormat.parseDuration("10h 3m 15s"));
    }
}


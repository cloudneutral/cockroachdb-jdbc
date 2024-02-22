package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

@Tags(value = {
        @Tag("all-test"),
        @Tag("unit-test")
})
public class LoggingRetryListenerTest {
    @Test
    public void whenCallingListener_expectLogMessages() {
        Logger loggerMock = Mockito.mock(Logger.class);

        RetryListener listener = new LoggingRetryListener(loggerMock);
        listener.beforeRetry("commit", 1, new SQLException("Disturbance!"), Duration.ofSeconds(11));
        listener.afterRetry("commit", 1, new SQLException("Disturbance!"), Duration.ofSeconds(22));
        listener.afterRetry("commit", 1, null, Duration.ofSeconds(14));

        Mockito.verify(loggerMock, Mockito.atMost(1)).warn(Mockito.anyString());
        Mockito.verify(loggerMock, Mockito.atMost(1)).error(Mockito.anyString());
        Mockito.verify(loggerMock, Mockito.atMost(1)).info(Mockito.anyString());
    }
}

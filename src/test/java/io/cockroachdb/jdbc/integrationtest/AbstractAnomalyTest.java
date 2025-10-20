package io.cockroachdb.jdbc.integrationtest;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

import io.cockroachdb.jdbc.integrationtest.support.ThreadPool;

public abstract class AbstractAnomalyTest extends AbstractIntegrationTest {
    protected static final int MAX_OFFSET_MILLIS = 500;

    protected static ThreadPool threadPool = ThreadPool.unboundedPool();

    public static void waitRandom() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    protected static <T> T awaitFuture(Future<T> future) throws SQLException {
        try {
            return threadPool.awaitFuture(future);
        } catch (TimeoutException e) {
            throw new SQLException("Timeout or execution exception", e);
        } catch (ExecutionException e) {
            throw new SQLException("Execution exception", e.getCause());
        }
    }
}

package io.cockroachdb.jdbc.test.anomaly;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.CockroachConnection;
import io.cockroachdb.jdbc.test.DatabaseFixture;
import io.cockroachdb.jdbc.test.util.PrettyText;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;

@DatabaseFixture(beforeTestScript = "db/anomaly/doctors-ddl.sql")
public class WriteSkewDoctorsTest extends AbstractAnomalyTest {
    @Test
    public void whenUpdatingShift_expectSerializationErrors() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // Transaction T1
        Future<Integer> t1 = threadPool.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                latch.await();
                int result = updateOnCall(connection, "alice", 1234);
                connection.commit();
                return result;
            }
        });

        // Transaction T2
        Future<Integer> t2 = threadPool.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                latch.await();
                int result = updateOnCall(connection, "bob", 1234);
                connection.commit();
                return result;
            }
        });

        latch.countDown();

        try {
            Assertions.assertTrue(t1.get() >= 2);
        } catch (ExecutionException e) {
            logger.warn(e.toString());
        }

        try {
            Assertions.assertTrue(t2.get() >= 2);
        } catch (ExecutionException e) {
            logger.warn(e.toString());
        }

        try (Connection connection = dataSource.getConnection()) {
            Assertions.assertEquals(1, getOnCallCount(connection, 1234));
        }
    }

    @Test
    public void whenUpdatingShiftConcurrently_expectSerializationErrors() throws Exception {
        int numThreads = 1_000;
        List<Future<Integer>> futures = new ArrayList<>();

        LoggingRetryListener retryListener = new LoggingRetryListener();

        IntStream.rangeClosed(1, numThreads).forEach(value -> {
            Future<Integer> f = threadPool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    if (connection.isWrapperFor(CockroachConnection.class)) {
                        connection.unwrap(CockroachConnection.class).getConnectionSettings()
                                .setRetryListener(retryListener);
                    }

                    connection.setAutoCommit(false);
                    int result = updateOnCall(connection, RANDOM.nextBoolean() ? "alice" : "bob", 1234);
                    connection.commit();
                    return result;
                }
            });
            futures.add(f);
        });

        Assertions.assertEquals(numThreads, futures.size());

        int commits = 0;
        List<Throwable> errors = new ArrayList<>();

        while (!futures.isEmpty()) {
            Future<Integer> f = futures.remove(0);
            try {
                f.get();
                Assertions.assertTrue(f.get() >= 1);
                commits++;
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            Assertions.assertTrue(getOnCallCount(connection, 1234) >= 1);
        }

        final int rollbacks = errors.size();

        logger.info("Listing top-5 of {} errors:", errors.size());
        errors.stream().limit(5).forEach(throwable -> {
            logger.warn(throwable.toString());
        });

        logger.info("Transactions: {}",
                PrettyText.rate("commit", commits, "rollback", rollbacks));
        logger.info("Retries: {}", PrettyText.rate(
                "success",
                singletonRetryListener.getTotalSuccessfulRetries(),
                "fail",
                singletonRetryListener.getTotalFailedRetries()));
        logger.info(rollbacks > 0 ? PrettyText.flipTableGently() : PrettyText.shrug());
    }

    /**
     * SELECT count(1) FROM doctors WHERE on_call = true AND shift_id = 1234
     * UPDATE doctors SET on_call = false WHERE name = 'alice' AND shift_id = 1234
     * UPDATE doctors SET on_call = false WHERE name = 'bob' AND shift_id = 1234
     */
    private static int updateOnCall(Connection connection, String doctor, int shiftId)
            throws SQLException {
        Assertions.assertFalse(connection.getAutoCommit());

        int onCall = getOnCallCount(connection, shiftId);
        if (onCall >= 2) {
            AbstractAnomalyTest.waitRandom();

            try (PreparedStatement ps = connection.prepareStatement("UPDATE doctors SET on_call = false "
                    + "WHERE name = ? AND shift_id = ?")) {
                ps.setObject(1, doctor);
                ps.setObject(2, shiftId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Wrong update count");
                }
            }
        }
        return onCall;
    }

    private static int getOnCallCount(Connection connection, int shiftId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM doctors "
                + "WHERE on_call = true AND shift_id = ?")) { // Cant use aggregation due to SFU
            ps.setObject(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                int c = 0;
                while (rs.next()) {
                    c++;
                }
                return c;
            }
        }
    }
}

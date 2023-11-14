package io.cockroachdb.jdbc.test.anomaly;

import java.math.BigDecimal;
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
import io.cockroachdb.jdbc.test.PrettyText;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;

@DatabaseFixture(beforeTestScript = "db/anomaly/bank-ddl.sql")
public class WriteSkewBankTest extends AbstractAnomalyTest {
    @Test
    public void whenOverwritingBalance_expectSerializationError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // Transaction T1
        Future<BigDecimal> t1 = threadPool.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);

                Assertions.assertFalse(connection.getAutoCommit());

                latch.await();

                BigDecimal result = debitAccount(connection, "alice", "asset", new BigDecimal(700));
                connection.commit();
                return result;
            }
        });

        // Transaction T2
        Future<BigDecimal> t2 = threadPool.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);

                Assertions.assertFalse(connection.getAutoCommit());

                latch.await();

                BigDecimal result = debitAccount(connection, "alice", "expense", new BigDecimal(700));
                connection.commit();
                return result;
            }
        });

        latch.countDown();

        boolean t1Fail = false;
        try {
            t1.get();
        } catch (ExecutionException e) {
            logger.error("", e.getCause());
            t1Fail = true;
        }

        boolean t2Fail = false;
        try {
            t2.get();
        } catch (ExecutionException e) {
            logger.error("", e.getCause());
            t2Fail = true;
        }

        Assertions.assertFalse(t1Fail && t2Fail, "Both T1 and T2 failed!");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            BigDecimal b1 = readBalance(connection, "alice");

            Assertions.assertEquals(1, b1.compareTo(BigDecimal.ZERO),
                    "Expected non-neg balance but was: " + b1);
        }
    }

    @Test
    public void whenOverwritingBalanceConcurrently_expectSerializationErrors() throws Exception {
        int numThreads = 1_000;
        List<Future<BigDecimal>> futures = new ArrayList<>();

        LoggingRetryListener retryListener = new LoggingRetryListener();

        IntStream.rangeClosed(1, numThreads).forEach(value -> {
            Future<BigDecimal> f = threadPool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    if (connection.isWrapperFor(CockroachConnection.class)) {
                        connection.unwrap(CockroachConnection.class).getConnectionSettings()
                                .setRetryListener(retryListener);
                    }
                    connection.setAutoCommit(false);
                    BigDecimal result = debitAccount(connection,
                            RANDOM.nextBoolean() ? "alice" : "bob", RANDOM.nextBoolean() ? "asset" : "expense",
                            new BigDecimal(700));
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
            Future<BigDecimal> f = futures.remove(0);
            try {
                Assertions.assertEquals(1, f.get().compareTo(BigDecimal.ZERO));
                f.get();
                commits++;
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            Assertions.assertEquals(1, readBalance(connection, "alice").compareTo(BigDecimal.ZERO));
            Assertions.assertEquals(1, readBalance(connection, "bob").compareTo(BigDecimal.ZERO));
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

    private static BigDecimal debitAccount(Connection connection, String name, String type, BigDecimal amount)
            throws SQLException {
        BigDecimal balance = readBalance(connection, name);
        waitRandom();
        if (balance.subtract(amount).compareTo(BigDecimal.ZERO) > 0) {
            waitRandom();
            try (PreparedStatement ps = connection.prepareStatement("update account set balance = balance - ? "
                    + "where name = ? and type=?")) {
                ps.setObject(1, amount);
                ps.setObject(2, name);
                ps.setObject(3, type);

                ps.executeUpdate();
            }
        }
        return balance;
    }

    private static BigDecimal readBalance(Connection connection, String name)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select sum(balance) from account where name=?")) {
            ps.setObject(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject(1, BigDecimal.class);
                } else {
                    throw new IllegalStateException("No account named " + name);
                }
            }
        }
    }
}

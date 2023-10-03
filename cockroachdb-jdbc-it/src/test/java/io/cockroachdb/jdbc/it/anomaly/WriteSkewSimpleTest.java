package io.cockroachdb.jdbc.it.anomaly;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.CockroachConnection;
import io.cockroachdb.jdbc.it.DatabaseFixture;
import io.cockroachdb.jdbc.it.util.util.PrettyText;
import io.cockroachdb.jdbc.retry.ConcurrentUpdateException;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;

@DatabaseFixture(beforeTestScript = "db/anomaly/bank-ddl.sql")
public class WriteSkewSimpleTest extends AbstractAnomalyTest {
    @Test
    public void whenReadingAndWritingWithOverlapConcurrently_expectEitherTransactionToFail() throws Exception {
        final String who = "user-1";

        final BigDecimal originalBalance;
        try (Connection c = dataSource.getConnection()) {
            originalBalance = readBalance(c, who);
        }

        LoggingRetryListener retryListener = new LoggingRetryListener();

        CountDownLatch countDownLatch = new CountDownLatch(1);

        Future<BigDecimal> f1 = threadPool.submit(() -> {
            BigDecimal result;
            try (Connection t1 = dataSource.getConnection()) {
                if (t1.isWrapperFor(CockroachConnection.class)) {
                    t1.unwrap(CockroachConnection.class).getConnectionSettings()
                            .setRetryListener(retryListener);
                }
                t1.setAutoCommit(false);
                countDownLatch.await();
                result = withdrawFunds(t1, who, "asset", new BigDecimal("175.00"));
                t1.commit();
            }
            return result;
        });

        Future<BigDecimal> f2 = threadPool.submit(() -> {
            BigDecimal result;
            try (Connection t2 = dataSource.getConnection()) {
                if (t2.isWrapperFor(CockroachConnection.class)) {
                    t2.unwrap(CockroachConnection.class).getConnectionSettings()
                            .setRetryListener(retryListener);
                }
                t2.setAutoCommit(false);
                countDownLatch.await();
                result = withdrawFunds(t2, who, "expense", new BigDecimal("175.00"));
                t2.commit();
            }
            return result;
        });

        countDownLatch.countDown();

        try {
            Assertions.assertEquals(1, f1.get().compareTo(BigDecimal.ZERO));
            Assertions.assertEquals(1, f2.get().compareTo(BigDecimal.ZERO));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(cause instanceof ConcurrentUpdateException, "Got: " + cause.toString());
        }

        BigDecimal expected = originalBalance.subtract(new BigDecimal("175.00")); // exactly one txn must succeed
        try (Connection c = dataSource.getConnection()) {
            Assertions.assertEquals(expected, readBalance(c, who));
        }

        logger.info("Retries: {}", PrettyText.rate(
                "success",
                singletonRetryListener.getTotalSuccessfulRetries(),
                "fail",
                singletonRetryListener.getTotalFailedRetries()));
    }

    private BigDecimal withdrawFunds(Connection connection, String who, String accountType, BigDecimal amount)
            throws SQLException {
        BigDecimal balance = readBalance(connection, who);
        waitRandom();
        if (balance.subtract(amount).compareTo(BigDecimal.ZERO) > 0) {
            addBalance(connection, who, accountType, amount.negate());
            return balance.subtract(amount);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal readBalance(Connection connection, String name)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "select sum(balance) from account where name=?")) {
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

    private void addBalance(Connection connection, String name, String type, BigDecimal delta)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("update account set balance = balance + ? "
                + "where name = ? and type=?")) {
            ps.setObject(1, delta);
            ps.setObject(2, name);
            ps.setObject(3, type);
            ps.executeUpdate();
        }
    }
}

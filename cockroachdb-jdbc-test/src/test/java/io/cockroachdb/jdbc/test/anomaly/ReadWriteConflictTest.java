package io.cockroachdb.jdbc.test.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.CockroachConnection;
import io.cockroachdb.jdbc.test.DatabaseFixture;
import io.cockroachdb.jdbc.test.util.PrettyText;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;

@DatabaseFixture(beforeTestScript = "db/anomaly/bank-ddl.sql")
public class ReadWriteConflictTest extends AbstractAnomalyTest {
    @Test
    public void whenCausingReadWriteConflicts_expectSerializationErrors() throws Exception {
        int numThreads = 1_000;
        List<Future<BigDecimal>> futures = new ArrayList<>();

        LoggingRetryListener retryListener = new LoggingRetryListener();

        IntStream.rangeClosed(1, numThreads).forEach(value -> {
            String who = "user-" + RANDOM.nextInt(1, 50);

            Future<BigDecimal> f = threadPool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    if (connection.isWrapperFor(CockroachConnection.class)) {
                        connection.unwrap(CockroachConnection.class).getConnectionSettings()
                                .setRetryListener(retryListener);
                    }

                    connection.setAutoCommit(false);

                    BigDecimal amount = BigDecimal.valueOf(RANDOM.nextDouble(1.00, 15.00))
                            .setScale(2, RoundingMode.HALF_EVEN);
                    amount = RANDOM.nextBoolean() ? amount.negate() : amount;

                    BigDecimal result = debitAccount(connection, who, "asset", amount);
                    Assertions.assertTrue(result.compareTo(BigDecimal.ZERO) > 0);

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
                f.get();
                Assertions.assertEquals(1, f.get().compareTo(BigDecimal.ZERO));
                commits++;
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            Assertions.assertEquals(1, readBalance(connection, "alice", "asset").compareTo(BigDecimal.ZERO));
            Assertions.assertEquals(1, readBalance(connection, "bob", "asset").compareTo(BigDecimal.ZERO));
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
        BigDecimal balance = readBalance(connection, name, type);
        BigDecimal newBalance = balance.add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) > 0) {
            try (PreparedStatement ps = connection.prepareStatement("update account set balance = ? "
                    + "where name = ? and type=?")) {
                ps.setObject(1, newBalance);
                ps.setObject(2, name);
                ps.setObject(3, type);
                ps.executeUpdate();
            }
        }
        return newBalance;
    }

    private static BigDecimal readBalance(Connection connection, String name, String type)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "select balance from account where name=? and type=?")) {
            ps.setObject(1, name);
            ps.setObject(2, type);
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

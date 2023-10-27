package io.cockroachdb.jdbc.test.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.test.DatabaseFixture;
import io.cockroachdb.jdbc.test.util.PrettyText;

@DatabaseFixture(beforeTestScript = "db/anomaly/bank-ddl.sql")
public class WriteWriteConflictTest extends AbstractAnomalyTest {
    @Test
    public void whenCausingWriteWriteConflicts_expectNoSerializationErrors() throws Exception {
        int numThreads = 1_000;
        List<Future<BigDecimal>> futures = new ArrayList<>();

        IntStream.rangeClosed(1, numThreads).forEach(value -> {
            String who = "user-" + RANDOM.nextInt(1, 50);

            Future<BigDecimal> f = threadPool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);

                    BigDecimal amount = new BigDecimal
                            (RANDOM.nextDouble(1.00, 15.00)).setScale(2, RoundingMode.HALF_EVEN);
                    amount = RANDOM.nextBoolean() ? amount.negate() : amount;
                    BigDecimal result = debitAccount(connection, who, "asset", amount);
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
                commits++;
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }

        int rollbacks = errors.size();

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
        try (PreparedStatement ps = connection.prepareStatement("update account set balance = balance + ? "
                + "where name = ? and type=?")) {
            ps.setObject(1, amount);
            ps.setObject(2, name);
            ps.setObject(3, type);
            ps.executeUpdate();
        }
        return amount;
    }
}

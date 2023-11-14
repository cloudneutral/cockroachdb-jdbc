package io.cockroachdb.jdbc.test.attic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.IntStream;

import io.cockroachdb.jdbc.test.AbstractIntegrationTest;
import io.cockroachdb.jdbc.test.PrettyText;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

@Disabled
public class HikariTest extends AbstractIntegrationTest {
    @Test
    public void whenStartingMoreThreadsThanMaxPoolSize_expectTimeout() throws Exception {
        Assertions.assertTrue(dataSource.isWrapperFor(HikariDataSource.class));

        int numThreads = 100;

        List<Future<Integer>> futures = new ArrayList<>();

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(50);

        IntStream.rangeClosed(1, numThreads).forEach(value -> {
            Future<Integer> f = executorService.submit(() -> {
                logger.info("Started thread #{} - get connection", value);
                try (Connection connection = dataSource.getConnection()) {
                    logger.info("Started thread #{} - got connection {}", value, connection);

                    connection.setAutoCommit(false);

                    try (PreparedStatement ps = connection.prepareStatement("select " + value)) {
                        ps.execute();
                    }

                    Thread.sleep(60000);

                    connection.commit();
                    logger.info("Finished thread #{} - close connection {}", value, connection);
                    return value;
                } catch (SQLException ex) {
                    logger.warn("Failed for thread #{} to get connection: {} ", value, ex.toString());
                    throw ex;
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
                commits++;
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }

        final int rollbacks = errors.size();

        logger.info("Listing top-5 of {} errors:", errors.size());
        errors.stream().limit(5).forEach(throwable -> {
            logger.warn(throwable.toString());
        });

        logger.info("Transactions: {}",
                PrettyText.rate("commit", commits, "rollback", rollbacks));
        logger.info(rollbacks > 0 ? PrettyText.flipTableGently() : PrettyText.shrug());
    }
}

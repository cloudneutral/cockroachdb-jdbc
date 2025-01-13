package io.cockroachdb.jdbc.integrationtest;

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

import io.cockroachdb.jdbc.integrationtest.support.AsciiText;
import io.cockroachdb.jdbc.integrationtest.support.DatabaseFixture;

@DatabaseFixture(beforeTestScript = "/db/intersecting-ddl.sql")
public class IntersectingDataTest extends AbstractAnomalyTest {
    private static void readAndWrite(Connection connection, int classA, int classB)
            throws SQLException {
        Assertions.assertNotEquals(classA, classB);

        try (PreparedStatement select = connection.prepareStatement(
                "select sum(value) from classroom where class_id=?")) {
            select.setObject(1, classA);

            long sum = 0;
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    sum = rs.getLong(1);
                }
            }

            waitRandom();

            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into classroom (class_id,value) values (?,?)")) {
                insert.setObject(1, classB);
                insert.setObject(2, sum);

                insert.executeUpdate();
            } catch (SQLException ex) {
                throw ex;
            }
        } catch (SQLException ex) {
            throw ex;
        }
    }

    @Test
    public void whenReadsAndWritesIntersect_expectSerializationErrors() throws Exception {
        int initialCount = 0;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "select count(1) from classroom")) {
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        initialCount = rs.getInt(1);
                    }
                }
            }
        }

        Assertions.assertEquals(4, initialCount);

        List<Future<Void>> futureList = new ArrayList<>();

        int numWorkers = 10;

        IntStream.rangeClosed(1, numWorkers).forEach(value -> {
            Future<Void> f = threadPool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    for (int i = 0; i < numWorkers; i++) {
                        readAndWrite(connection, i % 2 == 0 ? 1 : 2, i % 2 == 0 ? 2 : 1);
                    }
                    connection.commit();
                    return null;
                }
            });
            futureList.add(f);
        });

        int commits = 0;
        int rollbacks = 0;

        while (!futureList.isEmpty()) {
            try {
                futureList.remove(0).get();
                commits++;
            } catch (ExecutionException e) {
                logger.warn("OK: {}", e.getCause().toString());
                rollbacks++;
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "select count(1) from classroom")) {
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        Assertions.assertEquals(initialCount + commits * numWorkers, rs.getInt(1));
                    }
                }
            }
        }

        logger.info("Transactions: {}",
                AsciiText.rate("commit", commits, "rollback", rollbacks));
        logger.info("Retries: {}", AsciiText.rate(
                "success",
                getLoggingRetryListener().getTotalSuccessfulRetries(),
                "fail",
                getLoggingRetryListener().getTotalFailedRetries()));
        logger.info(rollbacks > 0 ? AsciiText.flipTableGently() : AsciiText.shrug());
    }
}


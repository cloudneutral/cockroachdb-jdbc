package io.cockroachdb.jdbc.interactivetest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.integrationtest.AbstractIntegrationTest;
import io.cockroachdb.jdbc.integrationtest.support.AnsiColor;
import io.cockroachdb.jdbc.integrationtest.support.AsciiText;
import io.cockroachdb.jdbc.integrationtest.support.BatchStatementSetter;
import io.cockroachdb.jdbc.integrationtest.support.ConnectionTemplate;
import io.cockroachdb.jdbc.integrationtest.support.DatabaseFixture;
import io.cockroachdb.jdbc.integrationtest.support.JdbcTemplate;
import io.cockroachdb.jdbc.util.DurationFormat;

@Order(1)
@DatabaseFixture(beforeTestScript = "/db/product-ddl.sql")
@Tag("interactive-test")
public class ConnectionRetryTest extends AbstractIntegrationTest {
    private final int NUM_PRODUCTS = 1_000;

    private String runTime = "30s";

    private String delayPerUpdate = "5s";

    @Order(1)
    @Test
    public void whenStartingTest_thenCreateProductCatalog() throws SQLException {
        JdbcTemplate jdbcTemplate = JdbcTemplate.from(dataSource);
        jdbcTemplate.execute("DELETE FROM product WHERE 1=1");

        jdbcTemplate.batchUpdate("INSERT INTO product (id,version,inventory,price,name,sku) values (?,?,?,?,?,?)",
                new BatchStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setInt(2, 0);
                        ps.setObject(3, 500);
                        ps.setObject(4, BigDecimal.TEN);
                        ps.setObject(5, "CockroachDB Unleashed 2nd Ed");
                        ps.setObject(6, UUID.randomUUID().toString());
                    }

                    @Override
                    public int getBatchSize() {
                        return NUM_PRODUCTS;
                    }
                });
    }

    @Order(2)
    @Test
    public void whenExecutingTransactionPeriodically_thenObserveRetriesOnConnectionErrors() throws SQLException {
        Duration runTimeDuration = DurationFormat.parseDuration(runTime);
        Duration delayPerUpdateDuration = DurationFormat.parseDuration(delayPerUpdate);
        Duration frequency = Duration.ofSeconds(30);

        AtomicInteger offset = new AtomicInteger();
        AtomicInteger limit = new AtomicInteger(100);
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger decrements = new AtomicInteger();
        AtomicInteger rollbacks = new AtomicInteger();

        JdbcTemplate jdbcTemplate = JdbcTemplate.from(dataSource);

        final Integer productCount = jdbcTemplate.queryForObject("select count(1) from product", Integer.class);
        final Long originalSum = jdbcTemplate.queryForObject("select sum(inventory) from product", Long.class);

        Assertions.assertTrue(productCount > 0, "No products?");

        System.out.println(AsciiText.format(AnsiColor.BRIGHT_GREEN, "----- DATABASE CONNECTION RETRY TEST -----"));
        AsciiText.println(AnsiColor.BRIGHT_YELLOW,
                "This test will slowly and periodically build up explicit transactions with product singleton updates and then commit.\n"
                + "While this build-up goes on, you can choose to kill or restart nodes or disable the load balancer to observe the effects.\n"
                + "A successful outcome is zero rollbacks and correct inventory sum (unless the retry attempts get exhausted).");
        AsciiText.println(AnsiColor.GREEN, "\t%,d products", productCount);
        AsciiText.println(AnsiColor.GREEN, "\t%,d original inventory", originalSum);
        AsciiText.println(AnsiColor.BRIGHT_YELLOW,
                "The test starts in 10 sec and runs for total of %d seconds every %d second.",
                runTimeDuration.toSeconds(),
                frequency.toSeconds());

        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors());

        executorService.scheduleAtFixedRate(() -> {
            offset.set(offset.get() % productCount);

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);

                List<Product> productList = new ArrayList<>();

                ConnectionTemplate.from(connection).select("select * from product offset " + offset.get() + " limit " + limit.get(),
                        rs -> {
                            while (rs.next()) {
                                Product product = new Product();
                                product.setId(rs.getObject("id", UUID.class));
                                product.setInventory(rs.getInt("inventory"));
                                product.setPrice(rs.getBigDecimal("price"));
                                product.setSku(rs.getString("sku"));
                                product.setName(rs.getString("name"));

                                productList.add(product);
                            }
                        });

                AsciiText.println(AnsiColor.BRIGHT_PURPLE,
                        "Updating %,d products in explicit transaction at offset %,d for approx %d sec - SHUTDOWN NODES / LB NOW AT ANY POINT",
                        productList.size(), offset.get(),
                        delayPerUpdateDuration.multipliedBy(productList.size()).toSeconds());

                int n = 0;
                for (Product product : productList) {
                    int rows = ConnectionTemplate.from(connection).update("update product set inventory=inventory-1 where id=?",
                            product.getId());
                    if (rows != 1) {
                        throw new SQLException("Expected 1 rows updated got " + rows);
                    }

                    try {
                        AsciiText.println(AnsiColor.BRIGHT_GREEN, "%s",
                                AsciiText.progressBar(productList.size(), ++n, "offset " + offset));
                        Thread.sleep(delayPerUpdateDuration.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                connection.commit();

                decrements.addAndGet(productList.size());
                commits.incrementAndGet();

                AsciiText.println(AnsiColor.BRIGHT_GREEN,
                        "Commit %,d products (%,d in total) at offset %d - waiting for next pass", productList.size(),
                        decrements.get(), offset.get());
            } catch (SQLException e) {
                System.out.println("SQL exception - not expected");
                e.printStackTrace();
                rollbacks.incrementAndGet();
            } catch (Exception e) {
                System.out.println("Uncategorized exception");
                e.printStackTrace();
            } finally {
                offset.addAndGet(limit.get());
            }
        }, 10, frequency.toSeconds(), TimeUnit.SECONDS);

        executorService.schedule(() -> {
            System.out.println("Time is up - shutdown tasks");
            executorService.shutdown();
        }, runTimeDuration.toSeconds(), TimeUnit.SECONDS);

        try {
            do {
                AsciiText.println(AnsiColor.BRIGHT_GREEN, "Awaiting completion for " + runTimeDuration);
            } while (!executorService.awaitTermination(runTimeDuration.toSeconds(), TimeUnit.SECONDS));

            AsciiText.println(AnsiColor.BRIGHT_GREEN, "Tasks completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        AsciiText.println(AnsiColor.BRIGHT_PURPLE, "Transactions: %s",
                AsciiText.rate("commit", commits.get(), "rollback", rollbacks.get()));

        AsciiText.println(AnsiColor.BRIGHT_PURPLE, "Retries: %s",
                AsciiText.rate("success", getLoggingRetryListener().getTotalSuccessfulRetries(),
                        "fail", getLoggingRetryListener().getTotalFailedRetries()));

        long actualSum = jdbcTemplate.queryForObject("select sum(inventory) from product", Long.class);
        long expectedSum = originalSum - decrements.get();

        AsciiText.println(AnsiColor.BRIGHT_PURPLE, "%,d inventory original", originalSum);
        AsciiText.println(AnsiColor.BRIGHT_PURPLE, "%,d inventory actual", actualSum);
        AsciiText.println(AnsiColor.BRIGHT_PURPLE, "%,d inventory expected", expectedSum);
        AsciiText.println(AnsiColor.BRIGHT_YELLOW,
                rollbacks.get() > 0 ? AsciiText.flipTableRoughly() : AsciiText.happy());

        Assertions.assertEquals(0, rollbacks.get());
        Assertions.assertEquals(originalSum - decrements.get(), actualSum);
    }
}

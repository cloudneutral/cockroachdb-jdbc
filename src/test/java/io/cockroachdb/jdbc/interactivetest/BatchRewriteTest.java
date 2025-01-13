package io.cockroachdb.jdbc.interactivetest;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;

import io.cockroachdb.jdbc.integrationtest.AbstractIntegrationTest;
import io.cockroachdb.jdbc.integrationtest.support.AsciiText;
import io.cockroachdb.jdbc.integrationtest.support.BatchStatementSetter;
import io.cockroachdb.jdbc.integrationtest.support.DatabaseFixture;
import io.cockroachdb.jdbc.integrationtest.support.JdbcTemplate;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(3)
@DatabaseFixture(beforeTestScript = "/db/product-ddl.sql")
@Tag("interactive-test")
public class BatchRewriteTest extends AbstractIntegrationTest {
    private static final int BATCH_SIZE = 32;

    private static final int NUM_PRODUCTS = BATCH_SIZE * 16;

    private List<Product> findAllProducts() {
        return JdbcTemplate.from(dataSource)
                .queryForList("select * from product order by id", (rs, rowNum) -> {
                    Product product = new Product();
                    product.setId(rs.getObject("id", UUID.class));
                    product.setVersion(rs.getInt("version"));
                    product.setInventory(rs.getInt("inventory"));
                    product.setPrice(rs.getBigDecimal("price"));
                    product.setSku(rs.getString("sku"));
                    product.setName(rs.getString("name"));
                    product.setDescription(rs.getString("description"));
                    product.setLastUpdatedAt(rs.getTimestamp("last_updated_at").toLocalDateTime());
                    return product;
                });
    }

    @Order(1)
    @Test
    public void whenStartTest_thenDeleteAllProducts() {
        JdbcTemplate.from(dataSource).execute("delete from product where 1 = 1");
    }

    @Order(2)
    @Test
    public void whenInsertBatches_thenObserveRewriteToArrays() {
        List<Product> products = new ArrayList<>();

        IntStream.rangeClosed(1, NUM_PRODUCTS).forEach(value -> {
            Product product = new Product();
            product.setId(UUID.randomUUID());
            product.setVersion(0);
            product.setInventory(1);
            product.setPrice(BigDecimal.TEN);
            product.setSku(UUID.randomUUID().toString());
            product.setName("CockroachDB Unleashed 2nd Ed");
            product.setDescription("The book of books");
            product.setLastUpdatedAt(LocalDateTime.now());

            products.add(product);
        });

        final Instant startTime = Instant.now();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);
        final AtomicInteger chunksCompleted = new AtomicInteger();

        logger.info("INSERT {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        JdbcTemplate.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {
                    int[] rv = JdbcTemplate.from(dataSource)
                            .batchUpdate("INSERT into product (id,version,inventory,price,sku,name) " +
                                         "values (?,?,?,?,?,?)",
                                    new BatchStatementSetter() {
                                        @Override
                                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                                            ps.setObject(1, UUID.randomUUID());
                                            ps.setObject(2, 0);
                                            ps.setObject(3, 2);
                                            ps.setObject(4, BigDecimal.TEN);
                                            ps.setObject(5, UUID.randomUUID().toString());
                                            ps.setObject(6, "CockroachDB Unleashed 2nd Ed");
                                        }

                                        @Override
                                        public int getBatchSize() {
                                            return chunk.size();
                                        }
                                    });

                    Assertions.assertEquals(BATCH_SIZE, rv.length);
                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));

                    System.out.printf("\r%s", AsciiText.progressBar(totalChunks, chunksCompleted.incrementAndGet(), chunk.size() + ""));
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), AsciiText.shrug());
    }

    @Order(3)
    @Test
    public void whenUpsertOnConflictBatches_thenObserveRewriteToArrays() {
        List<Product> products = findAllProducts();
        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        final Instant startTime = Instant.now();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);
        final AtomicInteger chunksCompleted = new AtomicInteger();

        logger.info("INSERT on conflict {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        JdbcTemplate.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {

                    AtomicInteger idx = new AtomicInteger();

                    // No-ops since each row already exist
                    int[] rv = JdbcTemplate.from(dataSource)
                            .batchUpdate("INSERT into product (id,version,inventory,price,sku,name) " +
                                         "values (?,?,?,?,?,?) " +
                                         "ON CONFLICT ON CONSTRAINT product_sku_key DO NOTHING",
                                    new BatchStatementSetter() {
                                        @Override
                                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                                            Product product = chunk.get(idx.getAndIncrement());
                                            ps.setObject(1, product.getId());
                                            ps.setObject(2, product.getVersion());
                                            ps.setObject(3, product.getInventory() + 123);
                                            ps.setObject(4, product.getPrice().multiply(BigDecimal.TEN));
                                            ps.setObject(5, product.getSku());
                                            ps.setObject(6, product.getName());
                                        }

                                        @Override
                                        public int getBatchSize() {
                                            return chunk.size();
                                        }
                                    });

                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
                    Assertions.assertEquals(0, rv.length);

                    System.out.printf("\r%s", AsciiText.progressBar(totalChunks, chunksCompleted.incrementAndGet(), chunk.size() + ""));
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), AsciiText.shrug());
    }

    @Order(4)
    @Test
    public void whenUpsertBatches_thenObserveRewriteToArrays() {
        List<Product> products = findAllProducts();
        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        final Instant startTime = Instant.now();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);
        final AtomicInteger chunksCompleted = new AtomicInteger();

        logger.info("UPSERT on conflict {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        JdbcTemplate.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {

                    AtomicInteger idx = new AtomicInteger();

                    // No-ops since each row already exist
                    int[] rv = JdbcTemplate.from(dataSource)
                            .batchUpdate("UPSERT into product (id,version,inventory,price,sku,name) " +
                                         "values (?,?,?,?,?,?)",
                                    new BatchStatementSetter() {
                                        @Override
                                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                                            Product product = chunk.get(idx.getAndIncrement());
                                            ps.setObject(1, product.getId());
                                            ps.setObject(2, product.getVersion());
                                            ps.setObject(3, product.getInventory() + 2);
                                            ps.setObject(4, product.getPrice().multiply(BigDecimal.TEN));
                                            ps.setObject(5, product.getSku());
                                            ps.setObject(6, product.getName());
                                        }

                                        @Override
                                        public int getBatchSize() {
                                            return chunk.size();
                                        }
                                    });

                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
                    Assertions.assertEquals(chunk.size(), rv.length);

                    System.out.printf("\r%s", AsciiText.progressBar(totalChunks, chunksCompleted.incrementAndGet(), chunk.size() + ""));
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), AsciiText.shrug());
    }

    @Order(5)
    @Test
    public void whenUpdateBatches_thenObserveRewriteToArrays() {
        List<Product> products = findAllProducts();
        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        final Instant startTime = Instant.now();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);
        final AtomicInteger chunksCompleted = new AtomicInteger();

        logger.info("UPDATE {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        JdbcTemplate.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {

                    AtomicInteger idx = new AtomicInteger();

                    // No-ops since each row already exist
                    int[] rv = JdbcTemplate.from(dataSource)
                            .batchUpdate("UPDATE product SET " +
                                         "inventory = ?, " +
                                         "price = ?, " +
                                         "description = ?, " +
                                         "last_updated_at = clock_timestamp() " +
                                         "WHERE id = ? and version = ?",
                                    new BatchStatementSetter() {
                                        @Override
                                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                                            Product product = chunk.get(idx.getAndIncrement());
                                            ps.setObject(1, product.getInventory() + 100);
                                            ps.setObject(2, product.getPrice().multiply(BigDecimal.TEN));
                                            ps.setObject(3, "hello");
                                            ps.setObject(4, product.getId());
                                            ps.setObject(5, product.getVersion());
                                        }

                                        @Override
                                        public int getBatchSize() {
                                            return chunk.size();
                                        }
                                    });

                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
                    Assertions.assertEquals(chunk.size(), rv.length);

                    System.out.printf("\r%s", AsciiText.progressBar(totalChunks, chunksCompleted.incrementAndGet(), chunk.size() + ""));
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), AsciiText.shrug());
    }

    @Order(6)
    @Test
    public void whenWrappingTest_thenObserveUpdatedValuesMatch() {
        List<Product> products = findAllProducts();

        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        products.forEach(product -> {
            Assertions.assertEquals(104, product.getInventory());
            Assertions.assertEquals(new BigDecimal("1000.00"), product.getPrice());
            Assertions.assertEquals("hello", product.getDescription());
        });
    }
}

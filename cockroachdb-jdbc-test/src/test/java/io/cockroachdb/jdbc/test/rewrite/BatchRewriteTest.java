package io.cockroachdb.jdbc.test.rewrite;

import io.cockroachdb.jdbc.test.AbstractIntegrationTest;
import io.cockroachdb.jdbc.test.DatabaseFixture;
import io.cockroachdb.jdbc.test.JdbcHelper;
import io.cockroachdb.jdbc.test.PrettyText;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Tag("batch-rewrite-test")
@DatabaseFixture(beforeTestScript = "db/batch/product-ddl.sql")
public class BatchRewriteTest extends AbstractIntegrationTest {
    private static final int BATCH_SIZE = 32;

    private static final int NUM_PRODUCTS = BATCH_SIZE * 16;

    private List<Product> findAllProducts() {
        return new JdbcTemplate(dataSource).query("select * from product order by id", (rs, rowNum) -> {
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
        logger.info("DELETE all products");

        new JdbcTemplate(dataSource).execute("delete from product where 1 = 1");
    }

    @Order(2)
    @Test
    public void whenInsertBatches_thenObserveRewriteToArrays() {
        logger.info("INSERT {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        Assertions.assertEquals(0, findAllProducts().size());

        SimpleJdbcInsert insert = new SimpleJdbcInsert(dataSource)
                .withTableName("product")
                .usingColumns("id", "version", "inventory", "price", "sku", "name", "description");

        IntStream.rangeClosed(1, NUM_PRODUCTS / BATCH_SIZE).forEach(value -> {
            SqlParameterSource[] batch = new SqlParameterSource[BATCH_SIZE];

            IntStream.range(0, BATCH_SIZE).forEach(v -> {
                SqlParameterSource parameterSource = new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("version", 0)
                        .addValue("inventory", 1)
                        .addValue("price", BigDecimal.ONE)
                        .addValue("sku", UUID.randomUUID().toString())
                        .addValue("name", "CockroachDB Unleashed 2nd Ed")
                        .addValue("description", "The book on CockroachDB");
                batch[v] = parameterSource;
            });

            int[] rv = insert.executeBatch(batch); // Should translate to batch inserts with arrays

            Assertions.assertEquals(BATCH_SIZE, rv.length);
            Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
        });
    }

    @Order(3)
    @Test
    public void whenUpsertOnConflictBatches_thenObserveRewriteToArrays() {
        logger.info("UPSERT on conflict {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        List<Product> products = findAllProducts();
        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        final String sku = products.get(0).getSku();

        final NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        final Instant startTime = Instant.now();
        final AtomicInteger n = new AtomicInteger();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);

        JdbcHelper.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {
                    System.out.printf("\r%s", PrettyText.progressBar(totalChunks, n.incrementAndGet(), chunk.size() + ""));

                    SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
                    AtomicInteger idx = new AtomicInteger();

                    chunk.forEach(product -> {
                        SqlParameterSource parameterSource = new MapSqlParameterSource()
                                .addValue("id", product.getId())
                                .addValue("version", product.getVersion())
                                .addValue("inventory", product.getInventory() + 100)
                                .addValue("price", product.getPrice().multiply(BigDecimal.ONE))
                                .addValue("sku", sku)
                                .addValue("name", product.getName());
                        batch[idx.getAndIncrement()] = parameterSource;
                    });

                    // All should be UPSERT no-ops
                    int[] rv = namedParameterJdbcTemplate.batchUpdate("INSERT into product (id,version,inventory,price,sku,name) " +
                                    "values (:id, :version, :inventory, :price, :sku, :name) " +
                                    "ON CONFLICT ON CONSTRAINT product_sku_key DO NOTHING",
                            batch);

                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
                    Assertions.assertEquals(0, rv.length);
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), PrettyText.shrug());
    }

    @Order(4)
    @Test
    public void whenUpsertBatches_thenObserveRewriteToArrays() {
        logger.info("UPSERT {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        List<Product> products = findAllProducts();
        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        final NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        final Instant startTime = Instant.now();
        final AtomicInteger n = new AtomicInteger();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);

        JdbcHelper.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {
                    System.out.printf("\r%s", PrettyText.progressBar(totalChunks, n.incrementAndGet(), chunk.size() + ""));

                    SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
                    AtomicInteger idx = new AtomicInteger();

                    chunk.forEach(product -> {
                        SqlParameterSource parameterSource = new MapSqlParameterSource()
                                .addValue("id", product.getId())
                                .addValue("version", product.getVersion())
                                .addValue("inventory", product.getInventory())
                                .addValue("price", product.getPrice())
                                .addValue("sku", product.getSku())
                                .addValue("name", product.getName());
                        batch[idx.getAndIncrement()] = parameterSource;
                    });

                    // All should be UPSERT no-ops
                    int[] rv = namedParameterJdbcTemplate.batchUpdate("UPSERT into product (id,version,inventory,price,sku,name) " +
                            "values (:id, :version, :inventory, :price, :sku, :name)", batch);

                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
                    Assertions.assertEquals(BATCH_SIZE, rv.length);
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), PrettyText.shrug());
    }

    @Order(5)
    @Test
    public void whenUpdateBatches_thenObserveRewriteToArrays() {
        logger.info("UPDATE {} products with batch size {}", NUM_PRODUCTS, BATCH_SIZE);

        List<Product> products = findAllProducts();
        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        final NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        final Instant startTime = Instant.now();
        final AtomicInteger n = new AtomicInteger();
        final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);

        JdbcHelper.chunkedStream(products.stream(), BATCH_SIZE)
                .forEach(chunk -> {
                    System.out.printf("\r%s", PrettyText.progressBar(totalChunks,
                            n.incrementAndGet(), chunk.size() + ""));

                    SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
                    AtomicInteger idx = new AtomicInteger();

                    chunk.forEach(product -> {
                        SqlParameterSource parameterSource = new MapSqlParameterSource()
                                .addValue("id", product.getId())
                                .addValue("version", product.getVersion())
                                .addValue("inventory", product.getInventory() + 1)
                                .addValue("price", product.getPrice().add(BigDecimal.ONE))
//                                .addValue("description", null) // invalidates rewrite
                                .addValue("description", null, Types.VARCHAR);
                        batch[idx.getAndIncrement()] = parameterSource;
                    });

                    int[] rv = namedParameterJdbcTemplate.batchUpdate("UPDATE product SET " +
                                    "inventory = :inventory, " +
                                    "price = :price, " +
                                    "description = :description, " +
                                    "last_updated_at = clock_timestamp() " +
                                    "WHERE id = :id and version = :version",
                            batch); // Should translate to batch upsert with arrays

                    Arrays.stream(rv).forEach(v -> Assertions.assertEquals(Statement.SUCCESS_NO_INFO, v));
                    Assertions.assertEquals(chunk.size(), rv.length);
                });

        logger.info("Completed in {}\n{}", Duration.between(startTime, Instant.now()), PrettyText.shrug());
    }

    @Order(6)
    @Test
    public void whenWrappingTest_thenObserveUpdatedValuesMatch() {
        List<Product> products = findAllProducts();

        Assertions.assertEquals(NUM_PRODUCTS, products.size());

        products.forEach(product -> {
            Assertions.assertEquals(2, product.getInventory());
            Assertions.assertEquals(new BigDecimal("2.00"), product.getPrice());
            Assertions.assertNull(product.getDescription());
        });
    }
}

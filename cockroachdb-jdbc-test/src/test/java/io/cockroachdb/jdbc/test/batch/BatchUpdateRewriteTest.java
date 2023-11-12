package io.cockroachdb.jdbc.test.batch;

import io.cockroachdb.jdbc.test.AbstractIntegrationTest;
import io.cockroachdb.jdbc.test.DatabaseFixture;
import io.cockroachdb.jdbc.test.util.JdbcHelper;
import io.cockroachdb.jdbc.test.util.PrettyText;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(2)
@Tag("batch-update-test")
@DatabaseFixture(beforeTestScript = "db/batch/product-ddl.sql")
public class BatchUpdateRewriteTest extends AbstractIntegrationTest {
    private static final int BATCH_SIZE = 32;

    private static final int PRODUCTS = BATCH_SIZE * 1500; // 48K total

    private List<Product> findAll(int limit) {
        return new JdbcTemplate(dataSource).query("select * from product "
                + "order by id limit " + limit, (rs, rowNum) -> {
            Product product = new Product();
            product.setId(rs.getObject("id", UUID.class));
            product.setInventory(rs.getInt("inventory"));
            product.setPrice(rs.getBigDecimal("price"));
            product.setSku(rs.getString("sku"));
            product.setName(rs.getString("name"));
            return product;
        });
    }

    @Order(1)
    @Test
    public void whenInsertProductsUsingBatches_thenSucceed() throws Exception {
        logger.info("INSERT {} products", PRODUCTS);

        SimpleJdbcInsert insert = new SimpleJdbcInsert(dataSource)
                .withTableName("product");

        IntStream.rangeClosed(1, PRODUCTS / BATCH_SIZE).forEach(value -> {
            SqlParameterSource[] batch = new SqlParameterSource[BATCH_SIZE];
            IntStream.range(0, BATCH_SIZE).forEach(v -> {
                SqlParameterSource parameterSource = new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("inventory", 1)
                        .addValue("price", BigDecimal.ONE)
                        .addValue("sku", UUID.randomUUID().toString())
                        .addValue("name", "CockroachDB Unleashed 2nd Ed");
                batch[v] = parameterSource;
            });
            insert.executeBatch(batch);
        });
    }

    @Order(2)
    @Test
    public void whenUpdateProductsUsingBatches_thenObserveRewriteToArrays() throws Exception {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        List<Product> products = findAll(PRODUCTS);

        Assertions.assertEquals(PRODUCTS, products.size(), "Not enough data?");

        Stream<List<Product>> chunks = JdbcHelper.chunkedStream(products.stream(), BATCH_SIZE);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            logger.info("UPDATE {} products using chunks of {}", PRODUCTS, BATCH_SIZE);

            final Instant startTime = Instant.now();
            final AtomicInteger n = new AtomicInteger();
            final int totalChunks = Math.round(products.size() * 1f / BATCH_SIZE);

            chunks.forEach(chunk -> {
                System.out.printf("\r%s", PrettyText.progressBar(totalChunks, n.incrementAndGet(), BATCH_SIZE + ""));

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE product SET inventory=?, price=? WHERE id=?")) {

                    for (Product product : chunk) {
                        ps.setInt(1, product.getInventory() + 1);
                        ps.setBigDecimal(2, product.getPrice().add(new BigDecimal("1.00")));
                        ps.setObject(3, product.getId());

                        ps.addBatch();

                    }
                    ps.executeBatch();
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });

            logger.info("Completed in {}\n{}",
                    Duration.between(startTime, Instant.now()),
                    PrettyText.shrug());
        }
    }
}

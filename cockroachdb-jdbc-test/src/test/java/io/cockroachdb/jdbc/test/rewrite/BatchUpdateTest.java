package io.cockroachdb.jdbc.test.rewrite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.cockroachdb.jdbc.test.AbstractIntegrationTest;
import io.cockroachdb.jdbc.test.DatabaseFixture;
import io.cockroachdb.jdbc.test.JdbcHelper;
import io.cockroachdb.jdbc.test.PrettyText;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(2)
@Tags(value = {
        @Tag("all-test"),
        @Tag("batch-update-test")
})
@DatabaseFixture(beforeTestScript = "db/batch/product-ddl.sql")
public class BatchUpdateTest extends AbstractIntegrationTest {
    private static final int PRODUCTS_PER_BATCH_COUNT = 10_000;

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
    @ParameterizedTest
    @ValueSource(ints = {
            1 << 4, 1 << 5, 1 << 6, 1 << 7, 1 << 8, 1 << 9, 1 << 10})
    public void whenUpdateUsingBatches_thenObserveSingletons(int batchSize) throws Exception {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        List<Product> products = findAll(PRODUCTS_PER_BATCH_COUNT);

        Assertions.assertEquals(PRODUCTS_PER_BATCH_COUNT, products.size(),
                "Not enough data - run the BatchInsertTest first");

        Stream<List<Product>> chunks = JdbcHelper.chunkedStream(products.stream(), batchSize);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            logger.info("UPDATE {} products using chunks of {}", PRODUCTS_PER_BATCH_COUNT, batchSize);

            final Instant startTime = Instant.now();
            final AtomicInteger n = new AtomicInteger();
            final int totalChunks = Math.round(products.size() * 1f / batchSize);

            chunks.forEach(chunk -> {
                System.out.printf("\r%s", PrettyText.progressBar(totalChunks, n.incrementAndGet(), batchSize + ""));

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE product SET inventory=?, price=? WHERE id=?")) {

                    chunk.forEach(product -> {
                        try {
                            product.addInventoryQuantity(1);
                            product.setPrice(product.getPrice().add(new BigDecimal("1.00")));

                            ps.setInt(1, product.getInventory());
                            ps.setBigDecimal(2, product.getPrice());
                            ps.setObject(3, product.getId());

                            ps.addBatch();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    ps.executeLargeBatch();  // There's no actual batching going on
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });

            logger.info("Completed in {}\n{}",
                    Duration.between(startTime, Instant.now()),
                    PrettyText.shrug());
        }
    }

    @Order(2)
    @ParameterizedTest
    @ValueSource(ints = {
            1 << 4, 1 << 5, 1 << 6, 1 << 7, 1 << 8, 1 << 9, 1 << 10})
    public void whenUpdateUsingArrays_thenObserveBatches(int batchSize) throws Exception {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        List<Product> products = findAll(PRODUCTS_PER_BATCH_COUNT);

        Assertions.assertEquals(PRODUCTS_PER_BATCH_COUNT, products.size(),
                "Not enough data - run the BatchInsertTest first");

        Stream<List<Product>> chunks = JdbcHelper.chunkedStream(products.stream(), batchSize);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            logger.info("UPDATE FROM {} products using chunks of {}", PRODUCTS_PER_BATCH_COUNT, batchSize);

            final Instant startTime = Instant.now();
            final AtomicInteger n = new AtomicInteger();
            final int totalChunks = Math.round(products.size() * 1f / batchSize);

            chunks.forEach(chunk -> {
                System.out.printf("\r%s", PrettyText.progressBar(totalChunks, n.incrementAndGet(), batchSize + ""));

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE product SET inventory=data_table.new_inventory, price=data_table.new_price "
                                + "FROM (select "
                                + "unnest(?) as id, "
                                + "unnest(?) as new_inventory, "
                                + "unnest(?) as new_price) as data_table "
                                + "WHERE product.id=data_table.id")) {
                    List<Integer> qty = new ArrayList<>();
                    List<BigDecimal> price = new ArrayList<>();
                    List<UUID> ids = new ArrayList<>();

                    chunk.forEach(product -> {
                        qty.add(product.addInventoryQuantity(1));
                        price.add(product.getPrice().add(new BigDecimal("1.00")));
                        ids.add(product.getId());
                    });

                    ps.setArray(1, ps.getConnection()
                            .createArrayOf("UUID", ids.toArray()));
                    ps.setArray(2, ps.getConnection()
                            .createArrayOf("BIGINT", qty.toArray()));
                    ps.setArray(3, ps.getConnection()
                            .createArrayOf("DECIMAL", price.toArray()));

                    ps.executeLargeUpdate(); // Actual batching
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            logger.info("Completed in {}\n{}",
                    Duration.between(startTime, Instant.now()),
                    PrettyText.shrug());
        }

    }
}

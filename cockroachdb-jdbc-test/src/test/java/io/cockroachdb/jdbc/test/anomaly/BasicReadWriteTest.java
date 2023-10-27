package io.cockroachdb.jdbc.test.anomaly;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cockroachdb.jdbc.test.DatabaseFixture;

@DatabaseFixture(beforeTestScript = "db/anomaly/bank-ddl.sql")
public class BasicReadWriteTest extends AbstractAnomalyTest {
    @Test
    public void whenExecutingSequentialJdbcOperations_expectNothingFunny() throws Exception {
        final String who = "user-1";
        final BigDecimal originalBalance;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            originalBalance = readBalance(c, who);
            addBalance(c, who, "asset", new BigDecimal("175.00"));
            addBalance(c, who, "expense", new BigDecimal("175.00").negate());

            c.commit();
        } catch (SQLException e) {
            Assertions.fail(e);
            return;
        }

        try (Connection c = dataSource.getConnection()) {
            Assertions.assertEquals(originalBalance, readBalance(c, who));
        }
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

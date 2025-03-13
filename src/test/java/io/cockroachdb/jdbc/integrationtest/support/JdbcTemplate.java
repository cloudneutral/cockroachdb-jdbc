package io.cockroachdb.jdbc.integrationtest.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

public class JdbcTemplate {
    public static JdbcTemplate from(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    public static <T> Stream<List<T>> chunkedStream(Stream<T> stream, int chunkSize) {
        AtomicInteger idx = new AtomicInteger();
        return stream.collect(Collectors.groupingBy(x -> idx.getAndIncrement() / chunkSize))
                .values().stream();
    }

    private final DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean execute(String sql, Object... params) throws RuntimeSQLException {
        try (Connection connection = dataSource.getConnection()) {
            return ConnectionTemplate.from(connection).execute(sql, params);
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    public int update(String sql, Object... params) throws RuntimeSQLException {
        try (Connection connection = dataSource.getConnection()) {
            return ConnectionTemplate.from(connection).update(sql, params);
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    public int[] batchUpdate(String sql, BatchStatementSetter batchStatementSetter) throws RuntimeSQLException {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);

            for (int batch = 0; batch < batchStatementSetter.getBatchSize(); batch++) {
                ps.clearParameters();
                batchStatementSetter.setValues(ps, batch);
                ps.addBatch();
            }

            return ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    public <T> T queryForObject(String sql, Class<T> clazz) throws RuntimeSQLException {
        try (Connection connection = dataSource.getConnection()) {
            return ConnectionTemplate.from(connection).selectForObject(sql, (rs, rowNum) -> rs.getObject(1, clazz));
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    public <T> List<T> queryForList(String sql, ResultSetExtractor<T> handler) throws RuntimeSQLException {
        try (Connection connection = dataSource.getConnection()) {
            return ConnectionTemplate.from(connection).selectForList(sql, handler);
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }
}

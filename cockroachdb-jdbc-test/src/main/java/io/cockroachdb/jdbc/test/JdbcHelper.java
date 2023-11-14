package io.cockroachdb.jdbc.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

public abstract class JdbcHelper {
    public static <T> Stream<List<T>> chunkedStream(Stream<T> stream, int chunkSize) {
        AtomicInteger idx = new AtomicInteger();
        return stream.collect(Collectors.groupingBy(x -> idx.getAndIncrement() / chunkSize))
                .values().stream();
    }

    public static void select(Connection connection, String sql, ResultSetHandler handler)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                handler.handle(rs);
            }
        }
    }

    public static int update(DataSource dataSource, String sql, Object... params)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return update(connection, sql, params);
        }
    }

    public static int update(Connection connection, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                ps.setObject(idx++, p);
            }
            return ps.executeUpdate();
        }
    }

    public static boolean execute(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            return ps.execute();
        }
    }

    private JdbcHelper() {
    }
}

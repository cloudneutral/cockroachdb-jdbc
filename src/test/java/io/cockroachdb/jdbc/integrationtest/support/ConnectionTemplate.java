package io.cockroachdb.jdbc.integrationtest.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConnectionTemplate {
    public static ConnectionTemplate from(Connection connection) {
        return new ConnectionTemplate(connection);
    }

    private final Connection connection;

    public ConnectionTemplate(Connection connection) {
        this.connection = connection;
    }

    public void select(String sql, ResultSetHandler handler)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                handler.handle(rs);
            }
        }
    }

    public <T> T selectForObject(String sql, ResultSetExtractor<T> handler)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return handler.extract(rs, 1);
                } else {
                    return null;
                }
            }
        }
    }

    public <T> List<T> selectForList(String sql, ResultSetExtractor<T> handler)
            throws SQLException {
        List<T> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                int row = 1;
                while (rs.next()) {
                    T t = handler.extract(rs, row++);
                    list.add(t);
                }
            }
        }
        return list;
    }

    public int update(String sql, Object... params)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                ps.setObject(idx++, p);
            }
            return ps.executeUpdate();
        }
    }

    public boolean execute(String sql, Object... params)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                ps.setObject(idx++, p);
            }
            return ps.execute();
        }
    }
}

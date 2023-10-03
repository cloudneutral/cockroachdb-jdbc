package io.cockroachdb.jdbc.demo;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionCallback<T> {
    T process(Connection connection) throws SQLException;
}

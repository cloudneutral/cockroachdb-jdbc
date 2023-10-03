package io.cockroachdb.jdbc.demo;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultSetCallback<T> {
    T process(ResultSet resultSet) throws SQLException;
}

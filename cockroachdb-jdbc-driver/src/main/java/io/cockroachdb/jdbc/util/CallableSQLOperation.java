package io.cockroachdb.jdbc.util;

import java.sql.SQLException;

@FunctionalInterface
public interface CallableSQLOperation<T> {
    T call() throws SQLException;
}
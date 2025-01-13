package io.cockroachdb.jdbc.util;

import java.sql.SQLException;

/**
 * A functional interface representing a java.util.{@link java.util.function.Supplier} for
 * JDBC resources that may throw SQLExceptions.
 *
 * @param <T> type of resource to supply
 * @author Kai Niemi
 */
@FunctionalInterface
public interface ResourceSupplier<T> {
    /**
     * Gets a result
     *
     * @return any result
     * @throws SQLException on any SQL exception
     */
    T get() throws SQLException;
}

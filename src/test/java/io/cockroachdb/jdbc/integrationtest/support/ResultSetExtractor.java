package io.cockroachdb.jdbc.integrationtest.support;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultSetExtractor<T> {
    T extract(ResultSet rs, int rowNum) throws SQLException;
}

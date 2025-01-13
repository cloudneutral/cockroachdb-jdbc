package io.cockroachdb.jdbc.integrationtest.support;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface BatchStatementSetter {
    void setValues(PreparedStatement ps, int i) throws SQLException;

    int getBatchSize();
}

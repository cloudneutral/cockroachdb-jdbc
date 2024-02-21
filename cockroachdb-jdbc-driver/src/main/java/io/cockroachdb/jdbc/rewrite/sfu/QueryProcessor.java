package io.cockroachdb.jdbc.rewrite.sfu;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface representing a SQL query processor for rewrites.
 *
 * @author Kai Niemi
 */
public interface QueryProcessor {
    /**
     * A no-op processor.
     */
    QueryProcessor PASS_THROUGH = new QueryProcessor() {
        @Override
        public String processQuery(Connection connection, String query) {
            return query;
        }

        @Override
        public boolean isTransactionScoped() {
            return false;
        }
    };

    String processQuery(Connection connection, String query) throws SQLException;

    boolean isTransactionScoped();
}

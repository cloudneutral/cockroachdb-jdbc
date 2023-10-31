package io.cockroachdb.jdbc.rewrite.update;

import io.cockroachdb.jdbc.rewrite.CockroachSQLParserFactory;
import org.junit.jupiter.api.Test;

public class UpdateRewriteTest {
    @Test
    public void whenParsingSimpleUpdate_expectNoErrors() {
        CockroachSQLParserFactory.parseQuery("UPDATE product SET inventory = ?1, price = ?2 WHERE 1=1");
    }

    @Test
    public void whenParsingComplexUpdate_expectNoErrors() {
        CockroachSQLParserFactory.parseQuery("UPDATE product SET inventory=?, price=?, version=version+1" +
                "WHERE id=? and version=?");

        String after = """
                UPDATE product SET inventory=dt.new_inventory, price=dt.new_price, version=product.version+dt.version
                FROM (select unnest(?) as new_inventory,
                             unnest(?) as new_price,
                             unnest(?) as id,
                             unnest(?) as version)
                             as dt
                WHERE product.id=dt.id and product.version=dt.version 
                """;
    }

    @Test
    public void whenParsingComplexUpdatePredicate_expectNoErrors() {
        CockroachSQLParserFactory.parseQuery("UPDATE product " +
                " SET inventory=?1, price=?2" +
                " WHERE id=?3 AND (inserted_at >= ?4 OR (inserted_at IS NULL))");
    }
}

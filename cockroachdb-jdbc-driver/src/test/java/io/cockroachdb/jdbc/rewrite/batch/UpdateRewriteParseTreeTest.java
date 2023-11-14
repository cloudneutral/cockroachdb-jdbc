package io.cockroachdb.jdbc.rewrite.batch;

import io.cockroachdb.jdbc.CockroachConnection;
import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.rewrite.CockroachParserFactory;
import io.cockroachdb.jdbc.rewrite.sfu.SelectForUpdateProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Tag("unit-test")
public class UpdateRewriteParseTreeTest {
    @Test
    public void whenUpdateWithConstantValueAndParameterBinding_expectRewrite()
            throws SQLException {
        String before = "UPDATE product SET inventory = 100, price = ? WHERE id = ?";

        String after = CockroachParserFactory.rewriteUpdateStatement(before);

        String expected = "UPDATE product SET inventory = 100, price = _dt.p1 " +
                "FROM (SELECT " +
                "UNNEST(?) AS p1, " +
                "UNNEST(?) AS p2) " +
                "AS _dt " +
                "WHERE product.id = _dt.p2";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());

        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.doNothing().when(preparedStatementMock).setObject(1, 50.00);
        Mockito.doNothing().when(preparedStatementMock).setObject(2, 123456);
        Mockito.when(preparedStatementMock.executeUpdate()).thenReturn(1);
        Mockito.when(connectionMock.prepareStatement(before))
                .thenReturn(preparedStatementMock);

        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setQueryProcessor(SelectForUpdateProcessor.PASS_THROUGH);

        try (CockroachConnection cockroachConnection = new CockroachConnection(connectionMock, connectionSettings)) {
            PreparedStatement ps = cockroachConnection.prepareStatement(before);
            ps.setObject(1, 50.00);
            ps.setObject(2, 123456);
            ps.executeUpdate();
        }

        // todo!
        Mockito.verify(connectionMock, Mockito.times(1)).prepareStatement(before);
    }

    @Test
    public void whenUpdateWithParameterBinding_expectRewrite() {
        String before = "UPDATE product SET inventory = ?, price = ? WHERE id = ?";

        String after = CockroachParserFactory.rewriteUpdateStatement(before);

        String expected = "UPDATE product SET inventory = _dt.p1, price = _dt.p2 " +
                "FROM (SELECT " +
                "UNNEST(?) AS p1, " +
                "UNNEST(?) AS p2, " +
                "UNNEST(?) AS p3) " +
                "as _dt " +
                "WHERE product.id = _dt.p3";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

    @Test
    public void whenUpdateWithParameterBindingAndFunctionWithMultipleArguments_expectRewrite() {
        String before = "UPDATE product SET inventory=?, price=?, version=?, " +
                "last_updated_at = with_min_timestamp(?,?) " +
                "WHERE id=? and version=?";

        String after = CockroachParserFactory.rewriteUpdateStatement(before);

        String expected = "update product set inventory = _dt.p1, price = _dt.p2, version = _dt.p3, " +
                "last_updated_at = with_min_timestamp(_dt.p4, _dt.p5) " +
                "from (select " +
                "unnest(?) as p1, " +
                "unnest(?) as p2, " +
                "unnest(?) as p3, " +
                "unnest(?) as p4, " +
                "unnest(?) as p5, " +
                "unnest(?) as p6, " +
                "unnest(?) as p7" +
                ") as _dt " +
                "where product.id = _dt.p6 and product.version = _dt.p7";

        System.out.println(after);

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

    @Test
    public void whenUpdateWithParameterBindingAndPredicates_expectRewrite() {
        String before = "UPDATE product SET inventory=?, price=?, version=?, " +
                "last_updated_at = with_min_timestamp(?,?) " +
                "WHERE id=? and version=? " +
                "AND (last_updated_at >= ? OR (last_updated_at IS NULL))";

        String after = CockroachParserFactory.rewriteUpdateStatement(before);

        String expected = "update product set inventory = _dt.p1, price = _dt.p2, version = _dt.p3, " +
                "last_updated_at = with_min_timestamp(_dt.p4, _dt.p5) " +
                "from (select " +
                "unnest(?) as p1, " +
                "unnest(?) as p2, " +
                "unnest(?) as p3, " +
                "unnest(?) as p4, " +
                "unnest(?) as p5, " +
                "unnest(?) as p6, " +
                "unnest(?) as p7, " +
                "unnest(?) as p8" +
                ") as _dt " +
                "where product.id = _dt.p6 and product.version = _dt.p7 " +
                "AND (product.last_updated_at >= _dt.p8 OR (product.last_updated_at IS NULL))";

        System.out.println(after);

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }
}

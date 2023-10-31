package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.rewrite.sfu.SelectForUpdateProcessor;

@Tag("unit-test")
public class CockroachStatementTest {
    @Test
    public void givenExplicitTransaction_whenDetectingSFUStatement_expectTransactionScopedSFU() throws SQLException {
        Statement statementMock = Mockito.mock(Statement.class);
        Connection connectionMock = Mockito.mock(Connection.class);

        Mockito.when(connectionMock.getAutoCommit()).thenReturn(false);

        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setQueryProcessor(SelectForUpdateProcessor.INSTANCE);

        Connection connection = new CockroachConnection(connectionMock, connectionSettings);
        Mockito.when(statementMock.getConnection()).thenReturn(connection);

        Statement statementUnderTest = new CockroachStatement(statementMock, connectionSettings);

        Assertions.assertEquals(SelectForUpdateProcessor.INSTANCE, connectionSettings.getQueryProcessor());

        statementUnderTest.execute("SET implicitSelectForUpdate=true");

        Assertions.assertNotEquals(SelectForUpdateProcessor.INSTANCE, connectionSettings.getQueryProcessor());
        Assertions.assertTrue(connectionSettings.getQueryProcessor().isTransactionScoped());

        connection.commit();

        Assertions.assertEquals(SelectForUpdateProcessor.PASS_THROUGH, connectionSettings.getQueryProcessor());
        Assertions.assertFalse(connectionSettings.getQueryProcessor().isTransactionScoped());
    }

    @Test
    public void givenExplicitTransaction_whenFlippingSFUStatement_expectTransactionScopedSFU() throws SQLException {
        Statement statementMock = Mockito.mock(Statement.class);
        Connection connectionMock = Mockito.mock(Connection.class);

        Mockito.when(connectionMock.getAutoCommit()).thenReturn(false);

        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setQueryProcessor(SelectForUpdateProcessor.INSTANCE);

        Connection connection = new CockroachConnection(connectionMock, connectionSettings);
        Mockito.when(statementMock.getConnection()).thenReturn(connection);

        Statement statementUnderTest = new CockroachStatement(statementMock, connectionSettings);

        Assertions.assertEquals(SelectForUpdateProcessor.INSTANCE, connectionSettings.getQueryProcessor());

        statementUnderTest.execute("SET implicitSelectForUpdate=true");

        Assertions.assertNotEquals(SelectForUpdateProcessor.INSTANCE, connectionSettings.getQueryProcessor());
        Assertions.assertEquals("select 1 FOR UPDATE",
                connectionSettings.getQueryProcessor().processQuery(connectionMock, "select 1"));
        Assertions.assertTrue(connectionSettings.getQueryProcessor().isTransactionScoped());

        statementUnderTest.execute("SET implicitSelectForUpdate=false");

        Assertions.assertEquals("select 1",
                connectionSettings.getQueryProcessor().processQuery(connectionMock, "select 1"));
        Assertions.assertTrue(connectionSettings.getQueryProcessor().isTransactionScoped());

        connection.commit();

        Assertions.assertEquals("select 1",
                connectionSettings.getQueryProcessor().processQuery(connectionMock, "select 1"));
        Assertions.assertFalse(connectionSettings.getQueryProcessor().isTransactionScoped());
    }

    @Test
    public void givenImplicitTransaction_whenDetectingSFUStatement_expectError() throws SQLException {
        Statement statementMock = Mockito.mock(Statement.class);
        Connection connectionMock = Mockito.mock(Connection.class);

        Mockito.when(connectionMock.getAutoCommit()).thenReturn(true);

        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setQueryProcessor(SelectForUpdateProcessor.INSTANCE);

        Connection connection = new CockroachConnection(connectionMock, connectionSettings);
        Mockito.when(statementMock.getConnection()).thenReturn(connection);

        Statement statementUnderTest = new CockroachStatement(statementMock, connectionSettings);

        Assertions.assertEquals(SelectForUpdateProcessor.INSTANCE, connectionSettings.getQueryProcessor());

        Assertions.assertThrowsExactly(InvalidConnectionException.class, () -> {
            statementUnderTest.execute("SET implicitSelectForUpdate=true");
        });
    }

    @Test
    public void givenImplicitTransaction_andPassTroughProcessor_whenDetectingSFUStatement_expectError() throws SQLException {
        Statement statementMock = Mockito.mock(Statement.class);
        Connection connectionMock = Mockito.mock(Connection.class);

        Mockito.when(connectionMock.getAutoCommit()).thenReturn(true);

        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setQueryProcessor(SelectForUpdateProcessor.PASS_THROUGH);

        Connection connection = new CockroachConnection(connectionMock, connectionSettings);
        Mockito.when(statementMock.getConnection()).thenReturn(connection);

        Statement statementUnderTest = new CockroachStatement(statementMock, connectionSettings);

        Assertions.assertEquals(SelectForUpdateProcessor.PASS_THROUGH, connectionSettings.getQueryProcessor());

        Assertions.assertThrowsExactly(InvalidConnectionException.class, () -> {
            statementUnderTest.execute("SET implicitSelectForUpdate=true");
        });
    }
}

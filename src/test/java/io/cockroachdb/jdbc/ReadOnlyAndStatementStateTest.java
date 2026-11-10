package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.rewrite.SelectForUpdateProcessor;

/**
 * Verifies read-only tracking for implicit select-for-update, the local handling of
 * driver-specific SET statements and savepoint naming.
 */
@Tags(value = {
        @Tag("unit-test")
})
public class ReadOnlyAndStatementStateTest {
    private static final String SELECT = "select * from account where id = 1";

    private Connection delegateMock;

    private Statement statementMock;

    private CockroachConnection connection;

    @BeforeEach
    public void setup() throws SQLException {
        delegateMock = Mockito.mock(Connection.class);
        statementMock = Mockito.mock(Statement.class);
        Mockito.when(delegateMock.createStatement()).thenReturn(statementMock);
        Mockito.when(statementMock.getConnection()).thenReturn(delegateMock);
        Mockito.when(statementMock.executeQuery(Mockito.anyString())).thenReturn(Mockito.mock(ResultSet.class));
        Mockito.when(delegateMock.prepareStatement(Mockito.anyString()))
                .thenReturn(Mockito.mock(PreparedStatement.class));

        ConnectionSettings settings = new ConnectionSettings()
                .setQueryProcessor(SelectForUpdateProcessor.INSTANCE);
        connection = new CockroachConnection(delegateMock, settings);
    }

    private void assertLocking(boolean expected) throws SQLException {
        Mockito.clearInvocations(delegateMock, statementMock);
        connection.prepareStatement(SELECT);
        Mockito.verify(delegateMock).prepareStatement(expected ? SELECT + " FOR UPDATE" : SELECT);

        connection.createStatement().executeQuery(SELECT);
        Mockito.verify(statementMock).executeQuery(expected ? SELECT + " FOR UPDATE" : SELECT);
    }

    @Test
    public void whenSetTransactionReadOnly_expectNoLockingUntilCommit() throws SQLException {
        assertLocking(true);

        connection.createStatement().execute("SET TRANSACTION READ ONLY");
        assertLocking(false);

        connection.commit();
        assertLocking(true);
    }

    @Test
    public void whenBeginReadOnlyOrAOST_expectNoLockingUntilRollback() throws SQLException {
        connection.createStatement().execute("BEGIN TRANSACTION READ ONLY");
        assertLocking(false);
        connection.rollback();
        assertLocking(true);

        connection.createStatement().execute("SET TRANSACTION AS OF SYSTEM TIME '-10s'");
        assertLocking(false);
        connection.rollback();
        assertLocking(true);
    }

    @Test
    public void whenSqlCommitOrRollback_expectTransactionReadOnlyCleared() throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("SET TRANSACTION READ ONLY");
        statement.execute("ROLLBACK TO SAVEPOINT cockroach_restart");
        assertLocking(false); // savepoint rollback keeps the transaction

        statement.execute("COMMIT");
        assertLocking(true);

        statement.executeUpdate("START TRANSACTION READ ONLY");
        assertLocking(false);
        statement.execute("rollback");
        assertLocking(true);
    }

    @Test
    public void whenSetTransactionReadWrite_expectLockingAgain() throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("SET TRANSACTION READ ONLY");
        assertLocking(false);
        statement.execute("SET TRANSACTION READ WRITE");
        assertLocking(true);
    }

    @Test
    public void whenSessionDefaultReadOnly_expectNoLockingAcrossTransactions() throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("SET default_transaction_read_only = on");
        assertLocking(false);
        connection.commit();
        assertLocking(false);

        statement.execute("SET default_transaction_read_only TO 'off'");
        assertLocking(true);

        statement.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
        assertLocking(false);
        statement.execute("RESET ALL");
        assertLocking(true);
    }

    @Test
    public void whenSetImplicitSelectForUpdate_expectNoResults() throws SQLException {
        Mockito.when(statementMock.getUpdateCount()).thenReturn(5);
        Mockito.when(statementMock.getResultSet()).thenReturn(Mockito.mock(ResultSet.class));
        Mockito.when(statementMock.getMoreResults()).thenReturn(true);

        Statement statement = connection.createStatement();
        Assertions.assertFalse(statement.execute("SET implicitSelectForUpdate = true"));
        Assertions.assertEquals(-1, statement.getUpdateCount());
        Assertions.assertEquals(-1L, statement.getLargeUpdateCount());
        Assertions.assertNull(statement.getResultSet());
        Assertions.assertFalse(statement.getMoreResults());
        Assertions.assertFalse(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT));

        Mockito.verify(statementMock, Mockito.never()).execute(Mockito.anyString());
        Mockito.verify(statementMock, Mockito.never()).getUpdateCount();

        // Subsequent executions delegate as usual
        Mockito.when(statementMock.execute("update t set x = 1")).thenReturn(false);
        Assertions.assertFalse(statement.execute("update t set x = 1"));
        Assertions.assertEquals(5, statement.getUpdateCount());
    }

    @Test
    public void whenSettingNamedSavepoint_expectNamePassedThrough() throws SQLException {
        Savepoint savepoint = Mockito.mock(Savepoint.class);
        Mockito.when(delegateMock.setSavepoint("my_savepoint")).thenReturn(savepoint);

        Assertions.assertSame(savepoint, connection.setSavepoint("my_savepoint"));
        Mockito.verify(delegateMock).setSavepoint("my_savepoint");
        Mockito.verify(delegateMock, Mockito.never()).setSavepoint();
    }
}

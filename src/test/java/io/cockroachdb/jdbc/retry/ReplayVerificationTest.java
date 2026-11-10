package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.rewrite.QueryProcessor;

/**
 * Verifies that replayed statement results observed by the application are compared, and that
 * transient errors raised during a replay lead to another retry attempt.
 */
@Tags(value = {
        @Tag("unit-test")
})
public class ReplayVerificationTest {
    private static final String UPDATE_A = "update a set v = v + 1 where id = 1 and version = 1";

    private static final String UPDATE_B = "update b set v = 2 where id = 2";

    private static SQLException conflict() {
        return new SQLException("Disturbance!", "40001");
    }

    private static Connection connectionMock(PreparedStatement a, PreparedStatement b) throws SQLException {
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.isValid(Mockito.anyInt())).thenReturn(true);
        Mockito.when(connection.prepareStatement(UPDATE_A)).thenReturn(a);
        Mockito.when(connection.prepareStatement(UPDATE_B)).thenReturn(b);
        return connection;
    }

    private static Connection proxy(Connection primary, Connection... retries) {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(5);
        strategy.setMaxBackoffTime(Duration.ZERO);

        ConnectionSettings settings = new ConnectionSettings()
                .setRetryStrategy(strategy)
                .setRetryListener(Mockito.mock(RetryListener.class))
                .setQueryProcessor(QueryProcessor.PASS_THROUGH);

        Iterator<Connection> it = new ArrayList<>(List.of(retries)).iterator();
        return ConnectionRetryInterceptor.proxy(primary, settings, it::next);
    }

    // Defect 2: update counts

    @Test
    public void whenUpdateCountChangesOnReplay_expectConcurrentUpdateException() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeUpdate()).thenReturn(1);
        Mockito.when(b1.executeUpdate()).thenThrow(conflict());

        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeUpdate()).thenReturn(0); // e.g. version check no longer matches

        Connection connection = proxy(connectionMock(a1, b1), connectionMock(a2, b2));

        Assertions.assertEquals(1, connection.prepareStatement(UPDATE_A).executeUpdate());
        PreparedStatement b = connection.prepareStatement(UPDATE_B);

        ConcurrentUpdateException ex = Assertions.assertThrows(ConcurrentUpdateException.class, b::executeUpdate);
        Assertions.assertEquals("40001", ex.getSQLState());
        Assertions.assertTrue(ex.getMessage().contains("executeUpdate"), ex.getMessage());
        Mockito.verify(b2, Mockito.never()).executeUpdate();
    }

    @Test
    public void whenUpdateCountUnchangedOnReplay_expectSuccess() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeUpdate()).thenReturn(1);
        Mockito.when(b1.executeUpdate()).thenThrow(conflict());

        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeUpdate()).thenReturn(1);
        Mockito.when(b2.executeUpdate()).thenReturn(1);

        Connection connection = proxy(connectionMock(a1, b1), connectionMock(a2, b2));

        Assertions.assertEquals(1, connection.prepareStatement(UPDATE_A).executeUpdate());
        Assertions.assertEquals(1, connection.prepareStatement(UPDATE_B).executeUpdate());
    }

    @Test
    public void whenBatchCountsChangeOnReplay_expectConcurrentUpdateException() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeBatch()).thenReturn(new int[] {1, 1});
        Mockito.when(b1.executeUpdate()).thenThrow(conflict());

        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeBatch()).thenReturn(new int[] {1, 0});

        Connection connection = proxy(connectionMock(a1, b1), connectionMock(a2, b2));

        PreparedStatement a = connection.prepareStatement(UPDATE_A);
        a.addBatch();
        a.addBatch();
        Assertions.assertArrayEquals(new int[] {1, 1}, a.executeBatch());

        PreparedStatement b = connection.prepareStatement(UPDATE_B);
        Assertions.assertThrows(ConcurrentUpdateException.class, b::executeUpdate);
    }

    @Test
    public void whenBatchCountsUnchangedOnReplay_expectSuccess() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeBatch()).thenReturn(new int[] {1, 1});
        Mockito.when(b1.executeUpdate()).thenThrow(conflict());

        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeBatch()).thenReturn(new int[] {1, 1});
        Mockito.when(b2.executeUpdate()).thenReturn(1);

        Connection connection = proxy(connectionMock(a1, b1), connectionMock(a2, b2));

        PreparedStatement a = connection.prepareStatement(UPDATE_A);
        a.addBatch();
        a.addBatch();
        a.executeBatch();
        Assertions.assertEquals(1, connection.prepareStatement(UPDATE_B).executeUpdate());
    }

    // Defect 5: transient errors during replay

    @Test
    public void whenSerializationConflictDuringReplay_expectAnotherRetryAttempt() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeUpdate()).thenReturn(1);
        Mockito.when(b1.executeUpdate()).thenThrow(conflict());

        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeUpdate()).thenThrow(conflict()); // conflict while replaying
        Connection retry1 = connectionMock(a2, b2);

        PreparedStatement a3 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b3 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a3.executeUpdate()).thenReturn(1);
        Mockito.when(b3.executeUpdate()).thenReturn(1);
        Connection retry2 = connectionMock(a3, b3);

        Connection connection = proxy(connectionMock(a1, b1), retry1, retry2);

        Assertions.assertEquals(1, connection.prepareStatement(UPDATE_A).executeUpdate());
        Assertions.assertEquals(1, connection.prepareStatement(UPDATE_B).executeUpdate());
        connection.commit();

        Mockito.verify(retry1).rollback();
        Mockito.verify(retry1).close();
        Mockito.verify(b2, Mockito.never()).executeUpdate();
        Mockito.verify(b3, Mockito.times(1)).executeUpdate();
        Mockito.verify(retry2).commit();
    }

    @Test
    public void whenChecksumFailureDuringReplay_expectNoFurtherRetry() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeUpdate()).thenReturn(1);
        Mockito.when(b1.executeUpdate()).thenThrow(conflict());

        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeUpdate()).thenReturn(2);
        Connection retry1 = connectionMock(a2, Mockito.mock(PreparedStatement.class));
        Connection retry2 = Mockito.mock(Connection.class);

        Connection connection = proxy(connectionMock(a1, b1), retry1, retry2);

        connection.prepareStatement(UPDATE_A).executeUpdate();
        PreparedStatement b = connection.prepareStatement(UPDATE_B);

        Assertions.assertThrows(ConcurrentUpdateException.class, b::executeUpdate);
        Mockito.verifyNoInteractions(retry2);
    }

    @Test
    public void whenNonTransientErrorDuringReplay_expectThrownWithRootCause() throws SQLException {
        PreparedStatement a1 = Mockito.mock(PreparedStatement.class);
        PreparedStatement b1 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a1.executeUpdate()).thenReturn(1);
        SQLException conflict = conflict();
        Mockito.when(b1.executeUpdate()).thenThrow(conflict);

        // Non-transient error that already has a cause
        SQLException duplicate = new SQLException("Duplicate key", "23505", new RuntimeException("cause"));
        PreparedStatement a2 = Mockito.mock(PreparedStatement.class);
        Mockito.when(a2.executeUpdate()).thenThrow(duplicate);
        Connection retry2 = Mockito.mock(Connection.class);

        Connection connection = proxy(connectionMock(a1, b1),
                connectionMock(a2, Mockito.mock(PreparedStatement.class)), retry2);

        connection.prepareStatement(UPDATE_A).executeUpdate();
        PreparedStatement b = connection.prepareStatement(UPDATE_B);

        SQLException ex = Assertions.assertThrows(SQLException.class, b::executeUpdate);
        Assertions.assertSame(duplicate, ex);
        Assertions.assertSame(conflict, ex.getSuppressed()[0]);
        Mockito.verifyNoInteractions(retry2);
    }
}

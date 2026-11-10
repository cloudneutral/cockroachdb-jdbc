package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
 * Verifies that result sets observed by the application remain verified (checksum) and
 * re-pointed to the current connection delegate across more than one retry attempt.
 */
@Tags(value = {
        @Tag("unit-test")
})
public class RepeatedRetryTest {
    private static final String SELECT = "select balance from account where id = 1";

    private static final String UPDATE = "update account set balance = 0 where id = 1";

    private static class Attempt {
        final Connection connection = Mockito.mock(Connection.class);

        final PreparedStatement select = Mockito.mock(PreparedStatement.class);

        final PreparedStatement update = Mockito.mock(PreparedStatement.class);

        final ResultSet resultSet = Mockito.mock(ResultSet.class);

        Attempt(int balance, boolean updateFails) throws SQLException {
            Mockito.when(connection.isValid(Mockito.anyInt())).thenReturn(true);
            Mockito.when(connection.prepareStatement(SELECT)).thenReturn(select);
            Mockito.when(connection.prepareStatement(UPDATE)).thenReturn(update);
            Mockito.when(select.executeQuery()).thenReturn(resultSet);
            Mockito.when(resultSet.next()).thenReturn(true);
            Mockito.when(resultSet.getInt(1)).thenReturn(balance);
            if (updateFails) {
                Mockito.when(update.executeUpdate()).thenThrow(new SQLException("Disturbance!", "40001"));
            } else {
                Mockito.when(update.executeUpdate()).thenReturn(1);
            }
        }
    }

    private static Connection proxy(Attempt primary, Attempt... retries) {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(5);
        strategy.setMaxBackoffTime(Duration.ZERO);

        ConnectionSettings settings = new ConnectionSettings()
                .setRetryStrategy(strategy)
                .setRetryListener(Mockito.mock(RetryListener.class))
                .setQueryProcessor(QueryProcessor.PASS_THROUGH);

        Iterator<Attempt> it = List.of(retries).iterator();
        return ConnectionRetryInterceptor.proxy(primary.connection, settings, () -> it.next().connection);
    }

    @Test
    public void whenReadChangesOnSecondRetry_expectChecksumFailure() throws SQLException {
        Attempt primary = new Attempt(10, true);
        Attempt retry1 = new Attempt(10, true);
        Attempt retry2 = new Attempt(11, false); // concurrent update observed

        Connection connection = proxy(primary, retry1, retry2);

        ResultSet rs = connection.prepareStatement(SELECT).executeQuery();
        Assertions.assertTrue(rs.next());
        Assertions.assertEquals(10, rs.getInt(1));

        PreparedStatement update = connection.prepareStatement(UPDATE);
        Assertions.assertThrows(ConcurrentUpdateException.class, update::executeUpdate);

        Mockito.verify(retry2.resultSet, Mockito.times(1)).getInt(1);
        Mockito.verify(retry2.update, Mockito.never()).executeUpdate();
    }

    @Test
    public void whenReadUnchangedOnSecondRetry_expectResultSetRepointedToLatestDelegate() throws SQLException {
        Attempt primary = new Attempt(10, true);
        Attempt retry1 = new Attempt(10, true);
        Attempt retry2 = new Attempt(10, false);

        Connection connection = proxy(primary, retry1, retry2);

        ResultSet rs = connection.prepareStatement(SELECT).executeQuery();
        Assertions.assertTrue(rs.next());
        Assertions.assertEquals(10, rs.getInt(1));

        Assertions.assertEquals(1, connection.prepareStatement(UPDATE).executeUpdate());

        // Application continues reading from its result set after both retries
        Assertions.assertEquals(10, rs.getInt(1));

        Mockito.verify(retry2.resultSet, Mockito.times(2)).getInt(1); // replay + application read
        Mockito.verify(retry1.resultSet, Mockito.times(1)).getInt(1); // replay only
        Mockito.verify(retry2.update, Mockito.times(1)).executeUpdate();
    }
}

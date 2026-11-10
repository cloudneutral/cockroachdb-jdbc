package io.cockroachdb.jdbc.retry;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;

@Tags(value = {
        @Tag("unit-test")
})
public class ResultSetHistoryFootprintTest {
    @Test
    public void whenReadingResultSet_expectHistoryWithoutValues() throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        ConnectionSettings settings = new ConnectionSettings()
                .setRetryStrategy(new ExponentialBackoffRetryStrategy())
                .setRetryListener(Mockito.mock(RetryListener.class));
        ConnectionRetryInterceptor connectionInterceptor
                = new ConnectionRetryInterceptor(connectionMock, settings, () -> null);

        ResultSet resultSetMock = Mockito.mock(ResultSet.class);
        Mockito.when(resultSetMock.next()).thenReturn(true);
        Mockito.when(resultSetMock.getString(1)).thenReturn("x".repeat(10_000));

        ResultSet rs = ResultSetRetryInterceptor.proxy(resultSetMock, connectionInterceptor);
        for (int i = 0; i < 100; i++) {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(10_000, rs.getString(1).length());
        }

        ResultSetRetryInterceptor interceptor = (ResultSetRetryInterceptor) Proxy.getInvocationHandler(rs);
        Assertions.assertEquals(200, interceptor.getMethodExecutions().size());
        interceptor.getMethodExecutions().forEach(execution -> {
            Assertions.assertNotNull(execution.getMethod());
            Assertions.assertNull(execution.getResult());
            Assertions.assertNull(execution.getTarget());
            Assertions.assertNull(execution.getConnectionInfo());
        });
    }
}

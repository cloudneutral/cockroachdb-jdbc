package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tags(value = {
        @Tag("all-test"),
        @Tag("unit-test")
})
public class DatabaseMetaDataProxyTest {
    @Test
    public void whenInspectingDatabaseMetadaata_expectCockroachDBMetadata() throws SQLException {
        DatabaseMetaData databaseMetaDataMock = Mockito.mock(DatabaseMetaData.class);

        Mockito.when(databaseMetaDataMock.supportsStoredProcedures())
                .thenReturn(false);
        Mockito.when(databaseMetaDataMock.getDatabaseProductName())
                .thenReturn("CockroachDB");

        Connection connectionMock1 = Mockito.mock(Connection.class);
        Connection connectionMock2 = Mockito.mock(Connection.class);
        Connection connectionMock3 = Mockito.mock(Connection.class);

        connectionMock(connectionMock1);
        connectionMock(connectionMock2);
        connectionMock(connectionMock3);

        Mockito.when(databaseMetaDataMock.getConnection())
                .thenReturn(connectionMock1, connectionMock2, connectionMock3);

        DatabaseMetaData proxy = DatabaseMetaDataProxy.proxy(databaseMetaDataMock);

        Assertions.assertFalse(proxy.supportsStoredProcedures());
        Assertions.assertEquals("CockroachDB", proxy.getDatabaseProductName());
        Assertions.assertEquals("CockroachDB CCL v22.1.10 (x86_64-pc-linux-gnu, built 2022/10/27 19:46:05, go1.17.11)",
                proxy.getDatabaseProductVersion());
        Assertions.assertEquals(22, proxy.getDatabaseMajorVersion());
        Assertions.assertEquals(1, proxy.getDatabaseMinorVersion());
        Assertions.assertEquals(CockroachDriverInfo.DRIVER_NAME, proxy.getDriverName());
        Assertions.assertEquals(CockroachDriverInfo.DRIVER_VERSION, proxy.getDriverVersion());
        Assertions.assertEquals(CockroachDriverInfo.MAJOR_VERSION, proxy.getDriverMajorVersion());
        Assertions.assertEquals(CockroachDriverInfo.MINOR_VERSION, proxy.getDriverMinorVersion());
        Assertions.assertEquals(CockroachDriverInfo.JDBC_MAJOR_VERSION, proxy.getJDBCMajorVersion());
        Assertions.assertEquals(CockroachDriverInfo.JDBC_MINOR_VERSION, proxy.getJDBCMinorVersion());
        Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE, proxy.getDefaultTransactionIsolation());
    }

    private Connection connectionMock(Connection connectionMock) throws SQLException {
        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);
        ResultSet resultSetMock = Mockito.mock(ResultSet.class);
        Mockito.when(resultSetMock.next())
                .thenReturn(true)
                .thenReturn(false);
        Mockito.when(resultSetMock.getString(1))
                .thenReturn("CockroachDB CCL v22.1.10 (x86_64-pc-linux-gnu, built 2022/10/27 19:46:05, go1.17.11)");
        Mockito.when(preparedStatementMock.executeQuery())
                .thenReturn(resultSetMock);
        Mockito.when(connectionMock.prepareStatement("select version()"))
                .thenReturn(preparedStatementMock);
        return connectionMock;
    }
}

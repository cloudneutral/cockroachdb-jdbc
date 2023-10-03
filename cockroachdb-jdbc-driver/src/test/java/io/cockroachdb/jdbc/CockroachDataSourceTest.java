package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tag("unit-test")
public class CockroachDataSourceTest {
    @Test
    public void whenDataSourceConnection_expectCorrectMapping() throws SQLException {
        Driver psqlDriverMock = Mockito.mock(Driver.class);
        Connection psqlConnectionMock = Mockito.mock(Connection.class);
        PreparedStatement psqlPreparedStatementMock = Mockito.mock(PreparedStatement.class);

        Mockito.doNothing().when(psqlPreparedStatementMock).close();
        Mockito.when(psqlConnectionMock.prepareStatement(Mockito.anyString()))
                .thenReturn(psqlPreparedStatementMock);

        Mockito.when(psqlDriverMock.acceptsURL("jdbc:postgresql")).thenReturn(true);
        Mockito.when(psqlDriverMock.connect(Mockito.startsWith("jdbc:postgresql"), Mockito.any(Properties.class)))
                .thenReturn(psqlConnectionMock);

        CockroachDriver cockroachDriver = new CockroachDriver();
        DriverManager.registerDriver(psqlDriverMock);
        DriverManager.registerDriver(cockroachDriver);

        DataSource dataSource = CockroachDataSource
                .builder()
                .withUrl("jdbc:cockroachdb://1.1.1.1:26257/jdbc_test?sslmode=disable")
                .withUsername("root")
                .withPassword("root")
                .withAutoCommit(true)
                .withImplicitSelectForUpdate(true)
                .withRetryTransientErrors(false)
                .withDataSourceProperties(dataSourceConfig -> {

                })
                .build();

        Connection connection = dataSource.getConnection();
        connection.prepareStatement("select 1").close();

        Mockito.verify(psqlDriverMock, Mockito.atMost(1)).connect(Mockito.anyString(), Mockito.any(Properties.class));
        Mockito.verify(psqlConnectionMock, Mockito.atMost(1)).getAutoCommit();
        Mockito.verify(psqlConnectionMock, Mockito.atMost(1)).prepareStatement(Mockito.anyString());
        Mockito.verify(psqlConnectionMock, Mockito.atMost(1)).close();

        DriverManager.deregisterDriver(psqlDriverMock);
        DriverManager.deregisterDriver(cockroachDriver);
    }

    @Test
    public void whenDataSourceConnectionWithBadURL_expectSQLException() throws SQLException {
        DataSource dataSource = CockroachDataSource
                .builder()
                .withUrl("jdbc:oracle://1.1.1.1:26257/jdbc_test")
                .withUsername("root")
                .withPassword("root")
                .withAutoCommit(true)
                .withImplicitSelectForUpdate(true)
                .withRetryTransientErrors(false)
                .withDataSourceProperties(dataSourceConfig -> {

                })
                .build();
        Assertions.assertNull(dataSource.getConnection());
    }
}

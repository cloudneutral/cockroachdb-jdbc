package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tag("unit-test")
public class ConnectionTest {
    @Test
    public void prepareStatement_ReturnResult_OnSimpleQuery() throws SQLException {
        Driver driverMock = Mockito.mock(Driver.class);
        Connection connectionMock = Mockito.mock(Connection.class);
        ResultSet resultSetMock = Mockito.mock(ResultSet.class);
        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);

        Mockito.when(resultSetMock.next())
                .thenReturn(true)
                .thenReturn(false);
        Mockito.when(resultSetMock.getInt(1))
                .thenReturn(1);
        Mockito.when(preparedStatementMock.executeQuery())
                .thenReturn(resultSetMock);
        Mockito.when(connectionMock.prepareStatement(Mockito.anyString()))
                .thenReturn(preparedStatementMock);
        Mockito.when(driverMock.acceptsURL("jdbc:postgresql")).thenReturn(true);
        Mockito.when(driverMock.connect(Mockito.startsWith("jdbc:postgresql"), Mockito.any(Properties.class)))
                .thenReturn(connectionMock);

        DriverManager.registerDriver(driverMock);
        DriverManager.registerDriver(new CockroachDriver());

        try (Connection connection = DriverManager.getConnection(
                "jdbc:cockroachdb://0.0.0.0:26257/jdbc_test?sslmode=disable")) {
            Assertions.assertFalse(connection.isClosed());
            Assertions.assertFalse(connection.isReadOnly());
            try (PreparedStatement ps = connection.prepareStatement("select 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Assertions.assertTrue(rs.next());
                    Assertions.assertEquals(1, rs.getInt(1));
                    Assertions.assertFalse(rs.next());
                }
            }
        }

        DriverManager.deregisterDriver(driverMock);
    }
}

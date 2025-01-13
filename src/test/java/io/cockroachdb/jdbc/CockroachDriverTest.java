package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tags(value = {
        @Tag("unit-test")
})
public class CockroachDriverTest {
    @Test
    public void getConnection_Valid_IfProperDatabaseURL() throws SQLException {
        Driver driverMock = Mockito.mock(Driver.class);
        Connection connectionMock = Mockito.mock(Connection.class);

        Mockito.when(driverMock.acceptsURL("jdbc:postgresql")).thenReturn(true);
        Mockito.when(driverMock.connect(Mockito.startsWith("jdbc:postgresql"), Mockito.any(Properties.class)))
                .thenReturn(connectionMock);

        DriverManager.registerDriver(driverMock);
        DriverManager.registerDriver(new CockroachDriver());

        try (Connection connection = DriverManager.getConnection(
                "jdbc:cockroachdb://0.0.0.0:26257/defaultdb?sslmode=disable")) {
            Assertions.assertFalse(connection.isClosed());
            Assertions.assertFalse(connection.isReadOnly());
        }

        DriverManager.deregisterDriver(driverMock);
    }

    @Test
    public void getVersion_Current() throws SQLException {
        Assertions.assertEquals(CockroachDriverInfo.MAJOR_VERSION,
                CockroachDriver.getRegisteredDriver().getMajorVersion());
        Assertions.assertEquals(CockroachDriverInfo.MINOR_VERSION,
                CockroachDriver.getRegisteredDriver().getMinorVersion());
    }

    @Test
    public void unregister_True_AlreadyRegistered() throws SQLException {
        Assertions.assertTrue(CockroachDriver.isRegistered());
        CockroachDriver.unregister();

        Assertions.assertFalse(CockroachDriver.isRegistered());
        Assertions.assertThrows(SQLException.class, CockroachDriver::getRegisteredDriver);

        CockroachDriver.register();

        Assertions.assertTrue(CockroachDriver.isRegistered());
        Assertions.assertNotNull(CockroachDriver.getRegisteredDriver());
    }

    @Test
    public void getDriverPropertyInfo_FullArray_WhenValidURL() throws SQLException {
        Assertions.assertTrue(CockroachDriver.isRegistered());

        DriverPropertyInfo[] arr = CockroachDriver.getRegisteredDriver().getPropertyInfo(
                "jdbc:cockroachdb://0.0.0.0:26257/defaultdb?sslmode=disable&retryTransientErrors=true",
                new Properties());

        List<DriverPropertyInfo> psql = new ArrayList<>();
        List<DriverPropertyInfo> crdb = new ArrayList<>();

        Arrays.stream(arr).sequential().forEach(driverPropertyInfo -> {
            Optional<CockroachProperty> p = Arrays.stream(CockroachProperty.values())
                    .filter(c -> c.getName().equals(driverPropertyInfo.name))
                    .findFirst();
            if (p.isPresent()) {
                if (p.get().equals(CockroachProperty.RETRY_TRANSIENT_ERRORS)) {
                    Assertions.assertEquals("true", driverPropertyInfo.value);
                }
                psql.add(driverPropertyInfo);
            } else {
                crdb.add(driverPropertyInfo);
            }
        });

        Assertions.assertEquals(12, psql.size());
        Assertions.assertEquals(80, crdb.size());
    }
}

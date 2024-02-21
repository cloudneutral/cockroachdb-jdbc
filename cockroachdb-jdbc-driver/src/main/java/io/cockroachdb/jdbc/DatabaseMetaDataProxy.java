package io.cockroachdb.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import io.cockroachdb.jdbc.util.CalendarVersion;

/**
 * An interceptor for {@code java.sql.DatabaseMetaData} that overrides a few methods
 * to provide CockroachDB related metadata.
 *
 * @author Kai Niemi
 */
public class DatabaseMetaDataProxy implements InvocationHandler {
    public static DatabaseMetaData proxy(DatabaseMetaData delegate) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaDataProxy.class.getClassLoader(),
                new Class[] {DatabaseMetaData.class},
                new DatabaseMetaDataProxy(delegate));
    }

    private final DatabaseMetaData delegate;

    protected DatabaseMetaDataProxy(DatabaseMetaData delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("supportsStoredProcedures".equals(method.getName())) {
            return false;
        }
        if ("getDatabaseProductName".equals(method.getName())) {
            return "CockroachDB";
        }
        if ("getDatabaseProductVersion".equals(method.getName())) {
            return retrieveServerVersion().orElse("n/a");
        }
        if ("getDatabaseMajorVersion".equals(method.getName())) {
            return retrieveServerVersion().map(version -> CalendarVersion.of(version).getMajor()).orElse(-1);
        }
        if ("getDatabaseMinorVersion".equals(method.getName())) {
            return retrieveServerVersion().map(version -> CalendarVersion.of(version).getMinor()).orElse(-1);
        }
        if ("getDriverName".equals(method.getName())) {
            return CockroachDriverInfo.DRIVER_NAME;
        }
        if ("getDriverVersion".equals(method.getName())) {
            return CockroachDriverInfo.DRIVER_VERSION;
        }
        if ("getDriverMajorVersion".equals(method.getName())) {
            return CockroachDriverInfo.MAJOR_VERSION;
        }
        if ("getDriverMinorVersion".equals(method.getName())) {
            return CockroachDriverInfo.MINOR_VERSION;
        }
        if ("getJDBCMajorVersion".equals(method.getName())) {
            return CockroachDriverInfo.JDBC_MAJOR_VERSION;
        }
        if ("getJDBCMinorVersion".equals(method.getName())) {
            return CockroachDriverInfo.JDBC_MINOR_VERSION;
        }
        if ("getDefaultTransactionIsolation".equals(method.getName())) {
            return Connection.TRANSACTION_SERIALIZABLE;
        }

        return method.invoke(delegate, args);
    }

    private Optional<String> retrieveServerVersion() throws SQLException {
        try (PreparedStatement ps = delegate.getConnection().prepareStatement("select version()")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString(1));
                }
                return Optional.empty();
            }
        }
    }
}

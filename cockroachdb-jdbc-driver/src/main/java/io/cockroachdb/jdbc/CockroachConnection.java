package io.cockroachdb.jdbc;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import io.cockroachdb.jdbc.rewrite.sfu.QueryProcessor;
import io.cockroachdb.jdbc.util.WrapperSupport;

/**
 * A {@code java.sql.Connection} implementation for CockroachDB, wrapping an underlying {@code PGConnection}.
 * Mostly acts a pass-through wrapper optionally rewriting SELECT queries and wrapping
 * statement's and preparedStatement's.
 */
public class CockroachConnection extends WrapperSupport<Connection> implements Connection {
    private final ConnectionSettings connectionSettings;

    public CockroachConnection(Connection delegate, ConnectionSettings connectionSettings) {
        super(delegate);
        this.connectionSettings = connectionSettings;
    }

    public ConnectionSettings getConnectionSettings() {
        return connectionSettings;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new CockroachStatement(getDelegate().createStatement(), connectionSettings);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        final String finalQuery = connectionSettings.getQueryProcessor().processQuery(this, sql);
        return getDelegate().prepareStatement(finalQuery);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("CockroachDB does not support stored procedures");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return getDelegate().nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        getDelegate().setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return getDelegate().getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        getDelegate().commit();
        checkState();
    }

    @Override
    public void rollback() throws SQLException {
        getDelegate().rollback();
        checkState();
    }

    @Override
    public void close() throws SQLException {
        getDelegate().close();
        checkState();
    }

    private void checkState() {
        if (connectionSettings.getQueryProcessor().isTransactionScoped()) {
            // Revert transaction scoped processor to pass-through
            connectionSettings.setQueryProcessor(QueryProcessor.PASS_THROUGH);
            logger.debug("Reverted implicit select-for-update to pass-through for connection delegate [{}]",
                    getDelegate());
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return getDelegate().isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return connectionSettings.isUseCockroachMetadata()
                ? DatabaseMetaDataProxy.proxy(getDelegate().getMetaData()) : getDelegate().getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        getDelegate().setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return getDelegate().isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        getDelegate().setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return getDelegate().getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        getDelegate().setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return getDelegate().getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return getDelegate().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        getDelegate().clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return new CockroachStatement(getDelegate()
                .createStatement(resultSetType, resultSetConcurrency), connectionSettings);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        String finalQuery = connectionSettings.getQueryProcessor().processQuery(this, sql);
        return new CockroachPreparedStatement(
                getDelegate().prepareStatement(finalQuery, resultSetType, resultSetConcurrency));
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("CockroachDB does not support stored procedures");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return getDelegate().getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        getDelegate().setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        getDelegate().setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return getDelegate().getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return getDelegate().setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return getDelegate().setSavepoint();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        getDelegate().rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        getDelegate().releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return new CockroachStatement(
                getDelegate().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability),
                connectionSettings);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        String finalQuery = connectionSettings.getQueryProcessor().processQuery(this, sql);
        return new CockroachPreparedStatement(
                getDelegate().prepareStatement(finalQuery, resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("CockroachDB does not support stored procedures");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        String finalQuery = connectionSettings.getQueryProcessor().processQuery(this, sql);
        return new CockroachPreparedStatement(
                getDelegate().prepareStatement(finalQuery, autoGeneratedKeys));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        String finalQuery = connectionSettings.getQueryProcessor().processQuery(this, sql);
        return new CockroachPreparedStatement(
                getDelegate().prepareStatement(finalQuery, columnIndexes));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        String finalQuery = connectionSettings.getQueryProcessor().processQuery(this, sql);
        return new CockroachPreparedStatement(
                getDelegate().prepareStatement(finalQuery, columnNames));
    }

    @Override
    public Clob createClob() throws SQLException {
        return getDelegate().createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return getDelegate().createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return getDelegate().createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return getDelegate().createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return getDelegate().isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        getDelegate().setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        getDelegate().setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return getDelegate().getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return getDelegate().getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return getDelegate().createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return getDelegate().createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        getDelegate().setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return getDelegate().getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        getDelegate().abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        getDelegate().setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return getDelegate().getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        checkClosed();
        return super.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        checkClosed();
        return super.isWrapperFor(iface);
    }

    protected void checkClosed() throws SQLException {
        if (isClosed()) {
            throw new ConnectionClosedException();
        }
    }
}
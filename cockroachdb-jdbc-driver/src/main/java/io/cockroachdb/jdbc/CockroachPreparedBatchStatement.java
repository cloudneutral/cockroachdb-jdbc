package io.cockroachdb.jdbc;

import io.cockroachdb.jdbc.retry.PreparedStatementRetryInterceptor;
import io.cockroachdb.jdbc.rewrite.CockroachSQLParserFactory;
import io.cockroachdb.jdbc.util.CallableSQLOperation;
import io.cockroachdb.jdbc.util.Pair;
import io.cockroachdb.jdbc.util.WrapperSupport;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.util.*;

/**
 * A {@code java.sql.PreparedStatement} implementation for CockroachDB, wrapping an underlying PgStatement
 * or proxy. Depending on API usage, in particular for batch statements, may perform rewrites of
 * UPDATE / INSERT / UPSERT queries to use arrays. This is done by deferring all calls to the delegate
 * until an execution method is used or addBatch, at which point its known what type of statement it is.
 */
public class CockroachPreparedBatchStatement extends WrapperSupport<PreparedStatement> implements PreparedStatement {
    static PreparedStatement emptyProxyDelegate() {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatementRetryInterceptor.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException();
                });
    }

    private final Connection connection;

    private final String query;

    private final List<ParameterRecord> parameterRecords = new ArrayList<>(64);

    private final List<Pair<String, List<Object>>> columnValues = new ArrayList<>();

    private static class ParameterRecord {
        CallableSQLOperation<?> operation;
        String sqlType;
        Object value;
    }

    public CockroachPreparedBatchStatement(Connection connection, String query) {
        super(emptyProxyDelegate());

        this.connection = connection;
        this.query = query;
    }

    private void addRowSetter(int parameterIndex, CallableSQLOperation<?> operation, int sqlType, Object value)
            throws SQLException {
        addRowSetter(parameterIndex, operation, JDBCType.valueOf(sqlType).getName(), value);
    }

    private <T> void addRowSetter(int parameterIndex, CallableSQLOperation<?> operation, String sqlType, T value)
            throws SQLException {
        if (isBatchRewriteVoided()) {
            throw new IllegalStateException();
        }
        ParameterRecord record = new ParameterRecord();
        record.operation = operation;
        record.sqlType = sqlType;
        record.value = value;

        if (parameterRecords.size() < parameterIndex) {
            parameterRecords.add(record);
        } else {
            parameterRecords.set(parameterIndex - 1, record);
        }
    }

    private boolean isBatchRewriteVoided() throws SQLException {
        return !Proxy.isProxyClass(super.getDelegate().getClass());
    }

    @Override
    protected PreparedStatement getDelegate() throws SQLException {
        if (Proxy.isProxyClass(super.getDelegate().getClass())) {
            setDelegate(connection.prepareStatement(this.query));

            // Invoke all deferred setXX calls
            for (ParameterRecord record : parameterRecords) {
                record.operation.call();
            }

            parameterRecords.clear();
        }
        return super.getDelegate();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return new CockroachResultSet(getDelegate().executeQuery());
    }

    @Override
    public int executeUpdate() throws SQLException {
        return getDelegate().executeUpdate();
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setNull(parameterIndex, sqlType);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setNull(parameterIndex, sqlType);
                return null;
            }, Types.NULL, null);
        }
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setBoolean(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setBoolean(parameterIndex, x);
                return null;
            }, Types.BOOLEAN, x);
        }
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setByte(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setByte(parameterIndex, x);
                return null;
            }, Types.TINYINT, x);
        }
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setShort(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setShort(parameterIndex, x);
                return null;
            }, Types.SMALLINT, x);
        }
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setInt(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setInt(parameterIndex, x);
                return null;
            }, Types.INTEGER, x);
        }

    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setLong(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setLong(parameterIndex, x);
                return null;
            }, Types.BIGINT, x);
        }
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setFloat(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setFloat(parameterIndex, x);
                return null;
            }, Types.REAL, x);
        }
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setDouble(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setDouble(parameterIndex, x);
                return null;
            }, Types.DOUBLE, x);
        }
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setBigDecimal(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setBigDecimal(parameterIndex, x);
                return null;
            }, Types.NUMERIC, x);
        }
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setString(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setString(parameterIndex, x);
                return null;
            }, Types.VARCHAR, x);
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setBytes(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setBytes(parameterIndex, x);
                return null;
            }, Types.LONGVARBINARY, x);
        }
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setDate(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setDate(parameterIndex, x);
                return null;
            }, Types.DATE, x);
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setTime(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setTime(parameterIndex, x);
                return null;
            }, Types.TIME, x);
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setTimestamp(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setTimestamp(parameterIndex, x);
                return null;
            }, Types.TIMESTAMP, x);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        getDelegate().setAsciiStream(parameterIndex, x, length);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        getDelegate().setUnicodeStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        getDelegate().setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void clearParameters() throws SQLException {
        parameterRecords.clear();
        if (isBatchRewriteVoided()) {
            getDelegate().clearParameters();
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setObject(parameterIndex, x, targetSqlType);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setObject(parameterIndex, x, targetSqlType);
                return null;
            }, targetSqlType, x);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setObject(parameterIndex, x);
            return;
        }

        if (x == null) {
            setNull(parameterIndex, Types.OTHER);
        } else if (x instanceof UUID) {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setObject(parameterIndex, x);
                return null;
            }, "UUID", (UUID) x);
        } else if (x instanceof SQLXML) {
            setSQLXML(parameterIndex, (SQLXML) x);
        } else if (x instanceof String) {
            setString(parameterIndex, (String) x);
        } else if (x instanceof BigDecimal) {
            setBigDecimal(parameterIndex, (BigDecimal) x);
        } else if (x instanceof Short) {
            setShort(parameterIndex, (Short) x);
        } else if (x instanceof Integer) {
            setInt(parameterIndex, (Integer) x);
        } else if (x instanceof Long) {
            setLong(parameterIndex, (Long) x);
        } else if (x instanceof Float) {
            setFloat(parameterIndex, (Float) x);
        } else if (x instanceof Double) {
            setDouble(parameterIndex, (Double) x);
        } else if (x instanceof byte[]) {
            setBytes(parameterIndex, (byte[]) x);
        } else if (x instanceof java.sql.Date) {
            setDate(parameterIndex, (java.sql.Date) x);
        } else if (x instanceof Time) {
            setTime(parameterIndex, (Time) x);
        } else if (x instanceof Timestamp) {
            setTimestamp(parameterIndex, (Timestamp) x);
        } else if (x instanceof Boolean) {
            setBoolean(parameterIndex, (Boolean) x);
        } else if (x instanceof Byte) {
            setByte(parameterIndex, (Byte) x);
        } else if (x instanceof Blob) {
            setBlob(parameterIndex, (Blob) x);
        } else if (x instanceof Clob) {
            setClob(parameterIndex, (Clob) x);
        } else if (x instanceof Array) {
            setArray(parameterIndex, (Array) x);
        } else if (x instanceof Character) {
            setString(parameterIndex, ((Character) x).toString());
        } else if (x instanceof Number) {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setObject(parameterIndex, x);
                return null;
            }, "DECIMAL", (Number) x);
        } else {
            getDelegate().setObject(parameterIndex, x);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        return getDelegate().execute();
    }

    @Override
    public void addBatch() throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().addBatch();
        } else {
            int index = 0;
            try {
                for (ParameterRecord record : parameterRecords) {
                    if (columnValues.size() <= index) {
                        List<Object> records = new ArrayList<>();
                        records.add(record.value);
                        columnValues.add(Pair.of(record.sqlType, records));
                    } else {
                        columnValues.get(index).getSecond().add(record.value);
                    }
                    index++;
                }
            } finally {
                parameterRecords.clear();
            }
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        getDelegate().setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setRef(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setRef(parameterIndex, x);
                return null;
            }, Types.REF, x);
        }
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setBlob(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setBlob(parameterIndex, x);
                return null;
            }, Types.BLOB, x);
        }
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setClob(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setClob(parameterIndex, x);
                return null;
            }, Types.CLOB, x);
        }
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setArray(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setArray(parameterIndex, x);
                return null;
            }, Types.ARRAY, x);
        }
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return getDelegate().getMetaData();
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        getDelegate().setDate(parameterIndex, x, cal);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        getDelegate().setTime(parameterIndex, x, cal);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        getDelegate().setTimestamp(parameterIndex, x, cal);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        getDelegate().setNull(parameterIndex, sqlType, typeName);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setURL(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setURL(parameterIndex, x);
                return null;
            }, Types.DATALINK, x);
        }
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return getDelegate().getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setRowId(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setRowId(parameterIndex, x);
                return null;
            }, Types.ROWID, x);
        }
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setNString(parameterIndex, value);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setNString(parameterIndex, value);
                return null;
            }, Types.NCHAR, value);
        }
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        getDelegate().setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setNClob(parameterIndex, value);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setNClob(parameterIndex, value);
                return null;
            }, Types.NCLOB, value);
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        getDelegate().setClob(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        getDelegate().setBlob(parameterIndex, inputStream, length);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        getDelegate().setNClob(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setSQLXML(parameterIndex, xmlObject);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setSQLXML(parameterIndex, xmlObject);
                return null;
            }, Types.SQLXML, xmlObject);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        getDelegate().setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        getDelegate().setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        getDelegate().setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        getDelegate().setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        getDelegate().setAsciiStream(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        getDelegate().setBinaryStream(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        getDelegate().setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        getDelegate().setNCharacterStream(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        getDelegate().setClob(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        getDelegate().setBlob(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        getDelegate().setNClob(parameterIndex, reader);
    }

    @Override
    public ResultSet executeQuery(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public int executeUpdate(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public void close() throws SQLException {
        getDelegate().close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return getDelegate().getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        getDelegate().setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return getDelegate().getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        getDelegate().setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        getDelegate().setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return getDelegate().getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        getDelegate().setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        getDelegate().cancel();
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
    public void setCursorName(String name) throws SQLException {
        getDelegate().setCursorName(name);
    }

    @Override
    public boolean execute(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ResultSet resultSet = getDelegate().getResultSet();
        return resultSet != null ? new CockroachResultSet(getDelegate().executeQuery()) : null;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return getDelegate().getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return getDelegate().getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        getDelegate().setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return getDelegate().getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        getDelegate().setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return getDelegate().getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return getDelegate().getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return getDelegate().getResultSetType();
    }

    @Override
    public void addBatch(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public void clearBatch() throws SQLException {
        getDelegate().clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        if (isBatchRewriteVoided()) {
            return getDelegate().executeBatch();
        }

        try {
            final String sql = CockroachSQLParserFactory.rewriteUpdateStatement(this.query);
            if (logger.isTraceEnabled()) {
                logger.trace("Rewriting SQL before executeBatch():\noriginal: {}\nnew: {}", this.query, sql);
            }

            // Replace delegate
            setDelegate(connection.prepareStatement(sql));

            int index = 1;
            for (Pair<String, List<Object>> pair : columnValues) {
                String type = pair.getFirst();
                List<Object> values = pair.getSecond();

                if (logger.isTraceEnabled()) {
                    logger.trace("Create array of type {} for column index {} with {} values",
                            type, index, values.size());
                }

                Array array = getDelegate().getConnection().createArrayOf(type, values.toArray());

                getDelegate().setArray(index++, array);
            }

            // Replace executeBatch
            int rowCount = getDelegate().executeUpdate();

            int[] rv = new int[columnValues.size()];
            Arrays.fill(rv, rowCount == columnValues.size() ? SUCCESS_NO_INFO : EXECUTE_FAILED);
            return rv;
        } finally {
            columnValues.clear();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (isBatchRewriteVoided()) {
            return getDelegate().getConnection();
        }
        return connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return getDelegate().getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return new CockroachResultSet(getDelegate().getGeneratedKeys());
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public boolean execute(String sql, String[] columnNames) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return getDelegate().getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return getDelegate().isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        getDelegate().setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return getDelegate().isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        getDelegate().closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return getDelegate().isCloseOnCompletion();
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
        getDelegate().setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
        getDelegate().setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        return getDelegate().executeLargeUpdate();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        return getDelegate().getLargeUpdateCount();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        getDelegate().setLargeMaxRows(max);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        return getDelegate().getLargeMaxRows();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        return getDelegate().executeLargeBatch();
    }

    @Override
    public long executeLargeUpdate(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }
}

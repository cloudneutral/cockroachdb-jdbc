package io.cockroachdb.jdbc;

import io.cockroachdb.jdbc.util.ResourceSupplier;
import io.cockroachdb.jdbc.util.WrapperSupport;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.util.*;

import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

/**
 * A {@code java.sql.PreparedStatement} implementation for CockroachDB, wrapping an underlying PgStatement
 * or proxy.
 * <p>
 * Depending on API usage and driver configuration, it may rewrite batch UPDATE / INSERT and UPSERT DML statements
 * to use SQL arrays. This is achieved by deferring the creation of the prepared statement delegate along with
 * most primitive (non-streaming) method calls to that delegate. When the executeBatch method is invoked and
 * the deferral is still active, it creates a rewritten DML prepared statement and binds array values to
 * that statement instead.
 * <p>
 * The pgjdbc has a hard batch size limit of 128 for rewriting INSERT statements. It doesn't rewrite UPSERTs
 * or UPDATES. Using this approach removes these limitations.
 *
 * @author Kai Niemi
 */
public class CockroachPreparedBatchStatement extends WrapperSupport<PreparedStatement> implements PreparedStatement {
    static PreparedStatement emptyProxyDelegate() {
        return (PreparedStatement) Proxy.newProxyInstance(
                CockroachPreparedBatchStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())
                            || "isWrapperFor".equals(method.getName())
                            || "unwrap".equals(method.getName())
                            || "hashCode".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException("Not supposed to happen! " +
                            "Please file a bug report:\nhttps://github.com/cloudneutral/cockroachdb-jdbc/issues");
                });
    }

    private final Connection connection;

    private final String query;

    private final String batchQuery;

    private final List<ParameterRecord> parameterRecords = new ArrayList<>(64);

    /**
     * Deferred addBatch() rows (one entry per command in the batch), each holding the
     * parameter records bound at the time. Kept as rows rather than columns so that the
     * batch can be replayed onto the original statement if the rewrite is voided.
     */
    private final List<List<ParameterRecord>> pendingRows = new ArrayList<>();

    private final boolean updateStatement;

    /**
     * The rewritten array statement, prepared on first batch execution and reused for subsequent
     * batches. It never replaces the logical (original) statement delegate.
     */
    private PreparedStatement arrayStatement;

    private boolean closed;

    private static class ParameterRecord {
        ResourceSupplier<?> operation;
        String sqlType;
        Object value;
    }

    public CockroachPreparedBatchStatement(Connection connection, String query, String batchQuery) {
        super(emptyProxyDelegate());

        this.connection = connection;
        this.query = query;
        this.batchQuery = batchQuery;
        this.updateStatement = query.trim().toLowerCase().startsWith("update");
    }

    private void addRowSetter(int parameterIndex, ResourceSupplier<?> operation, int sqlType, Object value)
            throws SQLException {
        addRowSetter(parameterIndex, operation, JDBCType.valueOf(sqlType).getName(), value);
    }

    private <T> void addRowSetter(int parameterIndex, ResourceSupplier<?> operation, String sqlType, T value)
            throws SQLException {
        if (isBatchRewriteVoided()) {
            throw new IllegalStateException();
        }
        checkNotClosed();

        ParameterRecord record = new ParameterRecord();
        record.operation = operation;
        record.sqlType = sqlType;
        record.value = value;

        // Attempt type qualification / cast
        if ("OTHER".equalsIgnoreCase(sqlType)) {
            try {
                // OTHER usually denotes a UUID so try that first
                record.value = UUID.fromString(Objects.toString(value));
                record.sqlType = "UUID";
            } catch (IllegalArgumentException e) {
                // Something else, possibly an enum which will likely fail later unless
                // an implicit type cast exits (not supported in CockroachDB since 25.1).
                // Ex: CREATE CAST (varchar AS order_status) WITH INOUT AS IMPLICIT;
                record.value = Objects.toString(value);
                record.sqlType = "VARCHAR";
            }
        }

        if (parameterIndex < 1) {
            throw new PSQLException("The parameter index is out of range: " + parameterIndex,
                    PSQLState.INVALID_PARAMETER_VALUE);
        }

        // Parameters may be set in any order, so address records by index and pad any gaps
        while (parameterRecords.size() < parameterIndex) {
            parameterRecords.add(null);
        }
        parameterRecords.set(parameterIndex - 1, record);
    }

    private PreparedStatement bindArrayStatement() throws SQLException {
        try {
            if (arrayStatement == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("""
                                    Creating batch array statement:
                                    Original query: {}
                                       Array query: {}""",
                            this.query, this.batchQuery);
                }
                arrayStatement = connection.prepareStatement(this.batchQuery);
            }

            final PreparedStatement preparedStatement = arrayStatement;
            final List<ParameterRecord> firstRow = pendingRows.get(0);

            for (List<ParameterRecord> row : pendingRows) {
                if (row.size() != firstRow.size()) {
                    throw new PSQLException("Inconsistent number of parameters in batch. Expected "
                            + firstRow.size() + " but was " + row.size(), PSQLState.INVALID_PARAMETER_VALUE);
                }
            }

            for (int column = 0; column < firstRow.size(); column++) {
                int index = column + 1;
                String type = firstRow.get(column).sqlType;
                List<Object> values = new ArrayList<>(pendingRows.size());
                for (List<ParameterRecord> row : pendingRows) {
                    values.add(row.get(column).value);
                }

                Array array = connection.createArrayOf(type, values.toArray());

                if (logger.isTraceEnabled()) {
                    logger.trace("Created array of type '{}' ({}) for column index {} with {} values",
                            array.getBaseTypeName(),
                            array.getBaseType(),
                            index,
                            values.size());
                }

                preparedStatement.setArray(index, array);
            }

            return preparedStatement;
        } finally {
            pendingRows.clear();
        }
    }

    private void checkNotClosed() throws SQLException {
        if (closed) {
            throw new PSQLException("This statement has been closed.", PSQLState.OBJECT_NOT_IN_STATE);
        }
    }

    private boolean isBatchRewriteVoided() throws SQLException {
        return !Proxy.isProxyClass(super.getDelegate().getClass());
    }

    private PreparedStatement getDelegate(String source) throws SQLException {
        if (Proxy.isProxyClass(super.getDelegate().getClass())) {
            if (logger.isTraceEnabled()) {
                logger.trace("Invalidating batch rewrite due to '{}' and replaying {} batch rows and {} recorded ops",
                        source, pendingRows.size(), parameterRecords.size());
            }
        }
        return getDelegate();
    }

    @Override
    protected final PreparedStatement getDelegate() throws SQLException {
        if (Proxy.isProxyClass(super.getDelegate().getClass())) {
            setDelegate(connection.prepareStatement(this.query));

            try {
                // Replay deferred batch rows onto the original statement
                for (List<ParameterRecord> row : pendingRows) {
                    for (ParameterRecord record : row) {
                        record.operation.get();
                    }
                    super.getDelegate().addBatch();
                }
                // Invoke all deferred setXX calls for the current (not yet batched) row
                for (ParameterRecord record : parameterRecords) {
                    if (record != null) {
                        record.operation.get();
                    }
                }
            } finally {
                pendingRows.clear();
                parameterRecords.clear();
            }
        }
        return super.getDelegate();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return new CockroachResultSet(getDelegate("executeQuery()").executeQuery());
    }

    @Override
    public int executeUpdate() throws SQLException {
        return getDelegate("executeUpdate()").executeUpdate();
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setNull(parameterIndex, sqlType);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setNull(parameterIndex, sqlType);
                return null;
            }, sqlType, null);
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
        getDelegate("setAsciiStream(parameterIndex,x,length)").setAsciiStream(parameterIndex, x, length);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        getDelegate("setUnicodeStream(parameterIndex,x,length)").setUnicodeStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        getDelegate("setBinaryStream(parameterIndex,x,length)").setBinaryStream(parameterIndex, x, length);
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
            setNull(parameterIndex, Types.NULL);
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
            getDelegate("setObject(parameterIndex,x)").setObject(parameterIndex, x);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        return getDelegate("execute()").execute();
    }

    @Override
    public void addBatch() throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().addBatch();
        } else {
            checkNotClosed();
            for (int i = 0; i < parameterRecords.size(); i++) {
                if (parameterRecords.get(i) == null) {
                    throw new PSQLException("No value specified for parameter " + (i + 1) + ".",
                            PSQLState.INVALID_PARAMETER_VALUE);
                }
            }

            try {
                pendingRows.add(new ArrayList<>(parameterRecords));
            } finally {
                parameterRecords.clear();
            }
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        getDelegate("setCharacterStream(parameterIndex,reader,length)").setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setRef(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setRef(parameterIndex, x);
                return null;
            }, x.getBaseTypeName(), x.getObject());
        }
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        getDelegate().setBlob(parameterIndex, x);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        getDelegate().setClob(parameterIndex, x);
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setArray(parameterIndex, x);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setArray(parameterIndex, x);
                return null;
            }, x.getBaseTypeName(), x.getArray());
        }
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return getDelegate("getMetaData()").getMetaData();
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        getDelegate("setDate(parameterIndex,x,cal)").setDate(parameterIndex, x, cal);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        getDelegate("setTime(parameterIndex,x,cal)").setTime(parameterIndex, x, cal);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        getDelegate("setTimestamp(parameterIndex,x,cal)").setTimestamp(parameterIndex, x, cal);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        getDelegate("setNull(parameterIndex,sqlType,typeName").setNull(parameterIndex, sqlType, typeName);
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
        return getDelegate("getParameterMetaData()").getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        getDelegate().setRowId(parameterIndex, x);
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
        getDelegate("setNCharacterStream(parameterIndex,value,length)").setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        getDelegate().setNClob(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        getDelegate("setClob(parameterIndex,reader,length)").setClob(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        getDelegate("setBlob(parameterIndex,inputStream,length)").setBlob(parameterIndex, inputStream, length);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        getDelegate("setNClob(parameterIndex,reader,length)").setNClob(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().setSQLXML(parameterIndex, xmlObject);
        } else {
            addRowSetter(parameterIndex, () -> {
                getDelegate().setSQLXML(parameterIndex, xmlObject);
                return null;
            }, Types.SQLXML, xmlObject.getString());
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        getDelegate("setObject(parameterIndex,x,targetSqlType,scaleOrLength)").setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        getDelegate("setAsciiStream(parameterIndex,x,length)").setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        getDelegate("setBinaryStream(parameterIndex,x,length)").setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        getDelegate("setCharacterStream(parameterIndex,reader,length)").setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        getDelegate("setAsciiStream(parameterIndex,x)").setAsciiStream(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        getDelegate("setBinaryStream(parameterIndex,x)").setBinaryStream(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        getDelegate("setCharacterStream(parameterIndex,reader)").setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        getDelegate("setNCharacterStream(parameterIndex,value)").setNCharacterStream(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        getDelegate("setClob(parameterIndex,reader)").setClob(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        getDelegate("setBlob(parameterIndex,inputStream)").setBlob(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        getDelegate("setNClob(parameterIndex, reader)").setNClob(parameterIndex, reader);
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
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (arrayStatement != null) {
                arrayStatement.close();
            }
        } finally {
            if (isBatchRewriteVoided()) {
                super.getDelegate().close();
            }
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return getDelegate("getMaxFieldSize()").getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        getDelegate("setMaxFieldSize(max)").setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return getDelegate("getMaxRows()").getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        getDelegate("setMaxRows(max)").setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        getDelegate("setEscapeProcessing(enable)").setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return getDelegate("getQueryTimeout()").getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        getDelegate("setQueryTimeout(seconds)").setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        getDelegate("cancel()").cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        if (isBatchRewriteVoided()) {
            return getDelegate().getWarnings();
        }
        return arrayStatement != null ? arrayStatement.getWarnings() : null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().clearWarnings();
        } else if (arrayStatement != null) {
            arrayStatement.clearWarnings();
        }
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        getDelegate("setCursorName(name)").setCursorName(name);
    }

    @Override
    public boolean execute(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ResultSet resultSet = getDelegate("getResultSet").getResultSet();
        return resultSet != null ? new CockroachResultSet(resultSet) : null;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return getDelegate("getUpdateCount()").getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return getDelegate("getMoreResults()").getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        getDelegate("setFetchDirection(direction)").setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return getDelegate("getFetchDirection()").getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        getDelegate("setFetchSize(rows)").setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return getDelegate("getFetchSize()").getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return getDelegate("getResultSetConcurrency()").getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return getDelegate("getResultSetType()").getResultSetType();
    }

    @Override
    public void addBatch(String sql) {
        throw new UnsupportedOperationException("Not supported for preparedStatement");
    }

    @Override
    public void clearBatch() throws SQLException {
        if (isBatchRewriteVoided()) {
            getDelegate().clearBatch();
        } else {
            pendingRows.clear();
        }
    }

    @Override
    public int[] executeBatch() throws SQLException {
        if (isBatchRewriteVoided()) {
            return getDelegate().executeBatch();
        }

        long[] counts = executeRewrittenBatch(false);
        int[] rv = new int[counts.length];
        for (int i = 0; i < counts.length; i++) {
            rv[i] = (int) counts[i];
        }
        return rv;
    }

    /**
     * Execute the deferred batch as a single array statement.
     *
     * @return update counts with one element per batched command, as mandated by JDBC
     */
    private long[] executeRewrittenBatch(boolean large) throws SQLException {
        checkNotClosed();
        final int commands = pendingRows.size();

        if (commands == 0) {
            return new long[0];
        }

        if (pendingRows.get(0).isEmpty()) {
            // No parameters to un-nest, so the rewritten statement cannot represent multiple
            // commands. Fall back to a regular batch on the original statement (which
            // replays the pending rows).
            PreparedStatement delegate = getDelegate("executeBatch() without parameters");
            return large ? delegate.executeLargeBatch()
                    : Arrays.stream(delegate.executeBatch()).asLongStream().toArray();
        }

        // The statement remains in deferred (rewrite) mode afterwards, so it can be reused
        // for subsequent batches with the same semantics.
        PreparedStatement statement = bindArrayStatement();

        long rowCount = large ? statement.executeLargeUpdate() : statement.executeUpdate();

        return toUpdateCounts(rowCount, commands);
    }

    /**
     * Derive per-command update counts from the total row count of the array statement.
     * Exact counts are only reported when they can be deduced, otherwise
     * {@link Statement#SUCCESS_NO_INFO} is used for every command.
     */
    private long[] toUpdateCounts(long rowCount, int commands) {
        long[] rv = new long[commands];
        if (rowCount == 0) {
            // Nothing affected by any command
            Arrays.fill(rv, 0);
        } else if (!updateStatement && rowCount == commands) {
            // Each INSERT/UPSERT command affects at most one row
            Arrays.fill(rv, 1);
        } else {
            // An UPDATE command may affect any number of rows, so the distribution is unknown
            Arrays.fill(rv, SUCCESS_NO_INFO);
        }
        return rv;
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
        return getDelegate("getMoreResults(current)").getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return new CockroachResultSet(getDelegate("getGeneratedKeys()").getGeneratedKeys());
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
        return getDelegate("getResultSetHoldability()").getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        if (isBatchRewriteVoided()) {
            return super.getDelegate().isClosed();
        }
        return closed;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        getDelegate("setPoolable(poolable)").setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return getDelegate("isPoolable()").isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        getDelegate("closeOnCompletion()").closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return getDelegate("isCloseOnCompletion()").isCloseOnCompletion();
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
        getDelegate("setObject(parameterIndex,x,targetSqlType,scaleOrLength)")
                .setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
        getDelegate("setObject(parameterIndex,x,targetSqlType)").setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        return getDelegate("executeLargeUpdate()").executeLargeUpdate();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        return getDelegate("getLargeUpdateCount()").getLargeUpdateCount();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        getDelegate("setLargeMaxRows(max)").setLargeMaxRows(max);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        return getDelegate("getLargeMaxRows()").getLargeMaxRows();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        if (isBatchRewriteVoided()) {
            return getDelegate().executeLargeBatch();
        }

        return executeRewrittenBatch(true);
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

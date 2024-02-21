package io.cockroachdb.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;

import io.cockroachdb.jdbc.util.WrapperSupport;

/**
 * A {@code java.sql.PreparedStatement} implementation for CockroachDB, wrapping an underlying PgStatement
 * or proxy.
 *
 * @author Kai Niemi
 */
public class CockroachPreparedStatement extends WrapperSupport<PreparedStatement> implements PreparedStatement {
    public CockroachPreparedStatement(PreparedStatement delegate) {
        super(delegate);
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
        getDelegate().setNull(parameterIndex, sqlType);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        getDelegate().setBoolean(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        getDelegate().setByte(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        getDelegate().setShort(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        getDelegate().setInt(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        getDelegate().setLong(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        getDelegate().setFloat(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        getDelegate().setDouble(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        getDelegate().setBigDecimal(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        getDelegate().setString(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        getDelegate().setBytes(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        getDelegate().setDate(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        getDelegate().setTime(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        getDelegate().setTimestamp(parameterIndex, x);
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
        getDelegate().clearParameters();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        getDelegate().setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        getDelegate().setObject(parameterIndex, x);
    }

    @Override
    public boolean execute() throws SQLException {
        return getDelegate().execute();
    }

    @Override
    public void addBatch() throws SQLException {
        getDelegate().addBatch();
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        getDelegate().setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        getDelegate().setRef(parameterIndex, x);
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
        getDelegate().setArray(parameterIndex, x);
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
        getDelegate().setURL(parameterIndex, x);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return getDelegate().getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        getDelegate().setRowId(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        getDelegate().setNString(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        getDelegate().setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        getDelegate().setNClob(parameterIndex, value);
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
        getDelegate().setSQLXML(parameterIndex, xmlObject);
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
        return getDelegate().executeBatch();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getDelegate().getConnection();
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

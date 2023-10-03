package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.util.PSQLState;

import io.cockroachdb.jdbc.query.QueryProcessor;
import io.cockroachdb.jdbc.query.SelectForUpdateProcessor;
import io.cockroachdb.jdbc.util.WrapperSupport;

/**
 * A {@code java.sql.Statement} implementation for CockroachDB, wrapping an underlying PgStatement
 * or proxy.
 */
public class CockroachStatement extends WrapperSupport<Statement> implements Statement {
    private static final Pattern SET_IMPLICIT_SFU = Pattern.compile(
            "SET\\s+implicitSelectForUpdate\\s*=\\s*(true|false).*", Pattern.CASE_INSENSITIVE);

    private final ConnectionSettings connectionSettings;

    public CockroachStatement(Statement delegate, ConnectionSettings connectionSettings) {
        super(delegate);
        this.connectionSettings = connectionSettings;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        final String finalQuery = connectionSettings.getQueryProcessor().processQuery(getConnection(), sql);
        return new CockroachResultSet(getDelegate().executeQuery(finalQuery));
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return getDelegate().executeUpdate(sql);
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
    public boolean execute(String sql) throws SQLException {
        Matcher matcher = SET_IMPLICIT_SFU.matcher(sql);
        if (matcher.matches()) {
            if (getConnection().getAutoCommit()) {
                throw new InvalidConnectionException(
                        "Implicit select-for-update requires explicit transactions (autoCommit=false)",
                        PSQLState.TRANSACTION_STATE_INVALID);
            }
            boolean onOff = Boolean.parseBoolean(matcher.group(1));

            if (onOff) {
                connectionSettings.setQueryProcessor(new SelectForUpdateProcessor() {
                    @Override
                    public boolean isTransactionScoped() {
                        return true;
                    }
                });
                getLogger().debug("Enabling implicit select-for-update for connection delegate [{}]", getConnection());
            } else {
                connectionSettings.setQueryProcessor(new QueryProcessor() {
                    @Override
                    public String processQuery(Connection connection, String query) {
                        return query;
                    }

                    @Override
                    public boolean isTransactionScoped() {
                        return true;
                    }
                });
                getLogger().debug("Disabling implicit select-for-update for connection delegate [{}]", getConnection());
            }
            // Don't pass statement to DB since it's not recognized
            return true;
        }
        return getDelegate().execute(sql);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ResultSet resultSet = getDelegate().getResultSet();
        return resultSet != null ? new CockroachResultSet(resultSet) : null;
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
    public void addBatch(String sql) throws SQLException {
        getDelegate().addBatch(sql);
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
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return getDelegate().executeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return getDelegate().executeUpdate(sql, columnIndexes);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return getDelegate().executeUpdate(sql, columnNames);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return getDelegate().execute(sql, autoGeneratedKeys);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return getDelegate().execute(sql, columnIndexes);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return getDelegate().execute(sql, columnNames);
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
    public long executeLargeUpdate(String sql) throws SQLException {
        return getDelegate().executeLargeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return getDelegate().executeLargeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return getDelegate().executeLargeUpdate(sql, columnIndexes);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return getDelegate().executeLargeUpdate(sql, columnNames);
    }
}
package io.cockroachdb.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.util.PSQLState;

import io.cockroachdb.jdbc.rewrite.QueryProcessor;
import io.cockroachdb.jdbc.rewrite.SelectForUpdateProcessor;
import io.cockroachdb.jdbc.util.WrapperSupport;

/**
 * A {@code java.sql.Statement} implementation for CockroachDB, wrapping an underlying PgStatement
 * or proxy.
 *
 * @author Kai Niemi
 */
public class CockroachStatement extends WrapperSupport<Statement> implements Statement {
    private static final Pattern SET_IMPLICIT_SFU = Pattern.compile(
            "SET\\s+implicitSelectForUpdate\\s*=\\s*(true|false).*", Pattern.CASE_INSENSITIVE);

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    private static final Pattern TRANSACTION_CHARACTERISTICS = Pattern.compile(
            "^\\s*(set\\s+transaction|begin|start\\s+transaction)\\b(.*)$", FLAGS);

    private static final Pattern SESSION_CHARACTERISTICS = Pattern.compile(
            "^\\s*set\\s+session\\s+characteristics\\s+as\\s+transaction\\b(.*)$", FLAGS);

    private static final Pattern SESSION_DEFAULT_READ_ONLY = Pattern.compile(
            "^\\s*set\\s+(session\\s+)?default_transaction_read_only\\s*(=|to)\\s*'?(\\w+)'?.*$", FLAGS);

    private static final Pattern SESSION_RESET = Pattern.compile(
            "^\\s*reset\\s+(all|default_transaction_read_only)\\b.*$", FLAGS);

    private static final Pattern READ_ONLY = Pattern.compile(
            "\\bread\\s+only\\b|\\bas\\s+of\\s+system\\s+time\\b", FLAGS);

    private static final Pattern READ_WRITE = Pattern.compile("\\bread\\s+write\\b", FLAGS);

    private static final Pattern TRANSACTION_END = Pattern.compile(
            "^\\s*(commit|end|abort|rollback(?!\\s+((work|transaction)\\s+)?to\\b))\\b.*$", FLAGS);

    private final ConnectionSettings connectionSettings;

    /**
     * True if the last execution was handled locally by the driver and produced no results.
     */
    private boolean noResults;

    public CockroachStatement(Statement delegate, ConnectionSettings connectionSettings) {
        super(delegate);
        this.connectionSettings = connectionSettings;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        noResults = false;
        final String finalQuery = connectionSettings.isReadOnlyBySQL()
                ? sql : connectionSettings.getQueryProcessor().processQuery(getConnection(), sql);
        return new CockroachResultSet(getDelegate().executeQuery(finalQuery));
    }

    /**
     * Track transaction and session characteristics set through SQL, which affect
     * the qualification of implicit select-for-update rewrites.
     */
    private void trackTransactionState(String sql) {
        Matcher m = TRANSACTION_CHARACTERISTICS.matcher(sql);
        if (m.matches()) {
            if (READ_ONLY.matcher(m.group(2)).find()) {
                connectionSettings.setTransactionReadOnly(true);
            } else if (READ_WRITE.matcher(m.group(2)).find()) {
                connectionSettings.setTransactionReadOnly(false);
            }
            return;
        }
        if (TRANSACTION_END.matcher(sql).matches()) {
            connectionSettings.setTransactionReadOnly(false);
            return;
        }
        m = SESSION_CHARACTERISTICS.matcher(sql);
        if (m.matches()) {
            if (READ_ONLY.matcher(m.group(1)).find()) {
                connectionSettings.setSessionReadOnly(true);
            } else if (READ_WRITE.matcher(m.group(1)).find()) {
                connectionSettings.setSessionReadOnly(false);
            }
            return;
        }
        m = SESSION_DEFAULT_READ_ONLY.matcher(sql);
        if (m.matches()) {
            String value = m.group(3).toLowerCase();
            connectionSettings.setSessionReadOnly("on".equals(value) || "true".equals(value));
            return;
        }
        if (SESSION_RESET.matcher(sql).matches()) {
            connectionSettings.setSessionReadOnly(false);
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        noResults = false;
        int rv = getDelegate().executeUpdate(sql);
        trackTransactionState(sql);
        return rv;
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
        noResults = false;
        Matcher matcher = SET_IMPLICIT_SFU.matcher(sql);
        if (matcher.matches()) {
            if (getConnection().getAutoCommit()) {
                throw new ConnectionInvalidException(
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
            // Don't pass statement to DB since it's not recognized. There's no result set
            // nor update count.
            noResults = true;
            return false;
        }
        boolean rv = getDelegate().execute(sql);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        if (noResults) {
            return null;
        }
        ResultSet resultSet = getDelegate().getResultSet();
        return resultSet != null ? new CockroachResultSet(resultSet) : null;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return noResults ? -1 : getDelegate().getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return !noResults && getDelegate().getMoreResults();
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
        noResults = false;
        return getDelegate().executeBatch();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getDelegate().getConnection();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return !noResults && getDelegate().getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return new CockroachResultSet(getDelegate().getGeneratedKeys());
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        noResults = false;
        int rv = getDelegate().executeUpdate(sql, autoGeneratedKeys);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        noResults = false;
        int rv = getDelegate().executeUpdate(sql, columnIndexes);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        noResults = false;
        int rv = getDelegate().executeUpdate(sql, columnNames);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        noResults = false;
        boolean rv = getDelegate().execute(sql, autoGeneratedKeys);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        noResults = false;
        boolean rv = getDelegate().execute(sql, columnIndexes);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        noResults = false;
        boolean rv = getDelegate().execute(sql, columnNames);
        trackTransactionState(sql);
        return rv;
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
        return noResults ? -1 : getDelegate().getLargeUpdateCount();
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
        noResults = false;
        return getDelegate().executeLargeBatch();
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        noResults = false;
        long rv = getDelegate().executeLargeUpdate(sql);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        noResults = false;
        long rv = getDelegate().executeLargeUpdate(sql, autoGeneratedKeys);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        noResults = false;
        long rv = getDelegate().executeLargeUpdate(sql, columnIndexes);
        trackTransactionState(sql);
        return rv;
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        noResults = false;
        long rv = getDelegate().executeLargeUpdate(sql, columnNames);
        trackTransactionState(sql);
        return rv;
    }
}
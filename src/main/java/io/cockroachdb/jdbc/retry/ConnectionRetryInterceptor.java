package io.cockroachdb.jdbc.retry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.postgresql.util.PSQLState;
import org.slf4j.MDC;

import io.cockroachdb.jdbc.CockroachPreparedStatement;
import io.cockroachdb.jdbc.CockroachStatement;
import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.ConnectionInvalidException;
import io.cockroachdb.jdbc.util.Assert;
import io.cockroachdb.jdbc.util.ExceptionUtils;
import io.cockroachdb.jdbc.util.ResourceSupplier;

/**
 * A dynamic proxy interceptor / invocation handler around java.sql.Connection. This interceptor
 * provides the main retry logic for all retryable SQL exception that may surface at commit time
 * or from any associated statements or result sets.
 *
 * @author Kai Niemi
 */
public class ConnectionRetryInterceptor extends AbstractRetryInterceptor<Connection> {
    public static Connection proxy(Connection connection,
                                   ConnectionSettings connectionSettings,
                                   ResourceSupplier<Connection> connectionSupplier) {
        return (Connection) Proxy.newProxyInstance(
                ConnectionRetryInterceptor.class.getClassLoader(),
                new Class[] {Connection.class},
                new ConnectionRetryInterceptor(connection, connectionSettings, connectionSupplier));
    }

    private final RetryListener retryListener;

    private final RetryStrategy retryStrategy;

    private final ConnectionSettings connectionSettings;

    private final ResourceSupplier<Connection> connectionSupplier;

    /**
     * Cached auto-commit mode of the current delegate, lazily resolved. Tracked locally
     * since the delegate may be unusable (closed or broken) at the time a retry decision
     * is made.
     */
    private Boolean autoCommit;

    /**
     * Open statements created by this connection, in creation order. Statements may span
     * transactions and must be recreated on a new connection delegate when retrying.
     */
    private final Set<AbstractStatementRetryInterceptor<?>> openStatements = new LinkedHashSet<>();

    protected ConnectionRetryInterceptor(Connection connection,
                                         ConnectionSettings connectionSettings,
                                         ResourceSupplier<Connection> connectionSupplier) {
        super(connection);

        this.connectionSettings = connectionSettings;
        this.connectionSupplier = connectionSupplier;

        this.retryListener = connectionSettings.getRetryListener();
        this.retryStrategy = connectionSettings.getRetryStrategy();

        setMethodTraceLogger(connectionSettings.getMethodTraceLogger());
    }

    protected ConnectionSettings getConnectionSettings() {
        return connectionSettings;
    }

    @Override
    protected String connectionInfo() {
        return connectionInfo(getDelegate());
    }

    /**
     * @return true if the connection is in auto-commit mode, in which case no method
     * executions are recorded and no transient errors are retried. Each statement is then
     * its own implicit transaction, and replaying previous (already committed) statements
     * would produce duplicate side effects.
     * @throws SQLException if the auto-commit mode cannot be resolved
     */
    protected boolean isAutoCommit() throws SQLException {
        if (autoCommit == null) {
            autoCommit = getDelegate().getAutoCommit();
        }
        return autoCommit;
    }

    /**
     * Record a method execution only when in an explicit transaction (auto-commit disabled).
     */
    protected void recordMethodExecution(AbstractRetryInterceptor<?> interceptor, MethodExecution context)
            throws SQLException {
        if (!isAutoCommit()) {
            interceptor.addMethodExecution(context);
        }
    }

    int openStatementCount() {
        return openStatements.size();
    }

    /**
     * Callback from statement interceptors when closed. Closed statements are retained until
     * the end of the current transaction since their executions may still need to be replayed.
     */
    void statementClosed(AbstractStatementRetryInterceptor<?> statementInterceptor) throws SQLException {
        if (isAutoCommit()) {
            openStatements.remove(statementInterceptor);
        }
    }

    /**
     * Clear the transaction history and compact the history of open statements to only
     * include the state that carries over to the next transaction.
     */
    private void endTransaction() {
        clearHistory();
        Iterator<AbstractStatementRetryInterceptor<?>> it = openStatements.iterator();
        while (it.hasNext()) {
            AbstractStatementRetryInterceptor<?> statementInterceptor = it.next();
            if (statementInterceptor.isClosed()) {
                it.remove();
            } else {
                statementInterceptor.endTransaction();
            }
        }
    }

    private Object createStatementProxy(Method method, Object[] args) throws Throwable {
        MethodExecution context = proceedWrapExecution(method, args);
        if (context.hasThrowable()) {
            throw context.getTargetException();
        }

        final boolean inTransaction = !isAutoCommit();
        if (inTransaction) {
            addMethodExecution(context);
        }

        final Object statementProxy;
        if ("prepareStatement".equals(method.getName())) {
            statementProxy = PreparedStatementRetryInterceptor.proxy(
                    new CockroachPreparedStatement((PreparedStatement) context.getResult()), this);
        } else {
            statementProxy = StatementRetryInterceptor.proxy(
                    new CockroachStatement((Statement) context.getResult(), connectionSettings), this);
        }

        AbstractStatementRetryInterceptor<?> statementInterceptor
                = (AbstractStatementRetryInterceptor<?>) Proxy.getInvocationHandler(statementProxy);
        statementInterceptor.setCreation(method, args, inTransaction);
        openStatements.add(statementInterceptor);

        context.setResult(statementProxy);
        return statementProxy;
    }

    protected String connectionInfo(Connection connection) {
        return "connection@" + Integer.toHexString(connection.hashCode());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("commit".equals(method.getName())) {
            final Instant startTime = Instant.now();

            for (int attempt = 1; ; attempt++) { // Limit defined by retry strategy
                try {
                    MethodExecution context = proceedWrapExecution(method, args);
                    if (context.hasThrowable()) {
                        throw context.getThrowable();
                    }
                    endTransaction();
                    return null;
                } catch (InvocationTargetException e) {
                    attempt = rollbackAndRetry(e.getTargetException(), method, attempt, startTime);
                }
            }
        } else if ("setAutoCommit".equals(method.getName())) {
            endTransaction();
            Object rv = proceedExecution(method, args);
            this.autoCommit = (Boolean) args[0];
            return rv;
        } else if ("rollback".equals(method.getName())) {
            endTransaction();
            return proceedExecution(method, args);
        } else if ("close".equals(method.getName())) {
            clearHistory();
            openStatements.clear();
            return proceedExecution(method, args);
        } else if ("prepareStatement".equals(method.getName())
                || "createStatement".equals(method.getName())) {
            return createStatementProxy(method, args);
        } else if ("toString".equals(method.getName())
                || "isWrapperFor".equals(method.getName())
                || "unwrap".equals(method.getName())
                || "hashCode".equals(method.getName())) {
            return proceed(method, args);
        }

        MethodExecution context = proceedWrapExecution(method, args);
        if (context.hasThrowable()) {
            throw context.getTargetException();
        }
        recordMethodExecution(this, context);
        return context.getResult();
    }

    protected final int rollbackAndRetry(Throwable targetException, Method method, int attempt, Instant startTime)
            throws Throwable {
        Assert.isTrue(attempt > 0, "attempt must be > 0");

        if (!(targetException instanceof SQLException)) {
            throw targetException;
        }

        final SQLException rootCauseException = (SQLException) targetException;

        if (!retryStrategy.isRetryableException(rootCauseException)) {
            throw rootCauseException;
        }

        // Retries are only safe within explicit transactions. In auto-commit mode each statement
        // is committed implicitly, so there is nothing to replay safely.
        boolean autoCommitMode;
        try {
            autoCommitMode = isAutoCommit();
        } catch (SQLException ex) {
            rootCauseException.addSuppressed(ex);
            throw rootCauseException;
        }
        if (autoCommitMode) {
            throw rootCauseException;
        }

        logger.debug("Entering retry attempt [{}] due to transient SQL exception:\n{}",
                attempt, ExceptionUtils.toNestedString(rootCauseException));

        for (; ; attempt++) {
            try {
                closeDelegate(attempt);
            } catch (SQLException ex) {
                // Unless it's a connection related error, we can't continue
                if (!retryStrategy.isConnectionError(ex)) {
                    throw new RollbackException("Exception on rollback before retry", ex);
                }
                // Let connection errors pass through with a warning since these are potentially retried
                logger.warn("SQL exception in rollback for connection delegate [{}]\n{}",
                        connectionInfo(), ExceptionUtils.toNestedString(ex));
            }

            if (!retryStrategy.proceedWithRetry(attempt)) {
                throw new TooManyRetriesException("Too many retry attempts [" + attempt
                        + "] or other limit in [" + retryStrategy.getDescription() + "]", rootCauseException);
            }

            Duration waitTime = retryStrategy.getBackoffDuration(attempt);

            MDC.put("retry.attempt", attempt + "");

            retryListener.beforeRetry(method.toGenericString(), attempt, rootCauseException, waitTime);

            // Pause current thread for a delay determined by strategy
            try {
                Thread.sleep(waitTime.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            SQLException retryException = rootCauseException;

            try {
                openDelegate(attempt);

                MDC.put("retry.connection", connectionInfo());

                // At this point we have a new, valid connection delegate and ready to replay history
                retry(getDelegate());
                // Signal success
                retryException = null;
                break;
            } catch (SQLException ex) {
                retryException = ex;
                // Unless it's a retryable error (serialization conflict during replay or connection
                // error) we can't continue. A checksum failure means the application has already
                // observed different results, so it's never retried.
                if (ex instanceof ConcurrentUpdateException
                        || !(retryStrategy.isConnectionError(ex) || retryStrategy.isRetryableException(ex))) {
                    throw withCause(ex, rootCauseException);
                }
                logger.debug("SQL exception in attempt [{}]\n{}",
                        attempt, ExceptionUtils.toNestedString(ex));
            } finally {
                retryListener.afterRetry(method.toGenericString(), attempt,
                        retryException,
                        Duration.between(startTime, Instant.now()));
                MDC.clear();
            }
        }

        return attempt;
    }

    /**
     * Attach the original exception as cause (or suppressed, if a cause is already set).
     */
    private static SQLException withCause(SQLException ex, SQLException rootCauseException) {
        if (ex == rootCauseException) {
            return ex;
        }
        if (ex.getCause() == null) {
            try {
                ex.initCause(rootCauseException);
                return ex;
            } catch (IllegalStateException e) {
                // Cause explicitly initialized to null
            }
        }
        ex.addSuppressed(rootCauseException);
        return ex;
    }

    private void openDelegate(int attempt) throws SQLException {
        logger.debug("Opening new connection for attempt [{}]", attempt);
        Connection newDelegate = connectionSupplier.get();
        if (newDelegate.getAutoCommit()) {
            throw new ConnectionInvalidException("Connection is in auto-commit mode",
                    PSQLState.UNEXPECTED_ERROR);
        }
        if (!newDelegate.isValid(10)) {
            throw new ConnectionInvalidException("Connection is invalid",
                    PSQLState.CONNECTION_UNABLE_TO_CONNECT);
        }
        SQLWarning warning = newDelegate.getWarnings();
        if (warning != null) {
            logger.warn("There are warnings:\n{}", ExceptionUtils.toNestedString(warning));
        }
        Connection expiredDelegate = getDelegate();
        setDelegate(newDelegate);
        logger.debug("Opened new connection [{}] replacing [{}]",
                connectionInfo(newDelegate), connectionInfo(expiredDelegate));
    }

    private void closeDelegate(int attempt) throws SQLException {
        Connection expiredDelegate = getDelegate();
        if (expiredDelegate.isClosed()) {
            logger.debug("Connection [{}] already closed",
                    connectionInfo(expiredDelegate));
        } else {
            logger.debug("Rollback and close connection [{}] for attempt [{}]",
                    connectionInfo(expiredDelegate), attempt);
            SQLWarning warning = expiredDelegate.getWarnings();
            if (warning != null) {
                logger.warn("There are warnings:\n{}", ExceptionUtils.toNestedString(warning));
            }
            expiredDelegate.rollback();
            expiredDelegate.close();
        }
    }

    @Override
    protected void doRetry(Iterable<MethodExecution> methodExecutions)
            throws Throwable {
        // Recreate statements carried over from previous transactions or auto-commit mode
        // (their creation is not part of the current transaction history)
        for (AbstractStatementRetryInterceptor<?> statementInterceptor : new ArrayList<>(openStatements)) {
            if (!statementInterceptor.isCreatedInTransaction()) {
                MethodExecution context = proceedWrapExecution(statementInterceptor.getCreationMethod(),
                        statementInterceptor.getCreationArgs());
                if (context.hasThrowable()) {
                    throw context.getTargetException();
                }
                statementInterceptor.retryWith(context.getResult());
            }
        }

        for (MethodExecution methodExecution : methodExecutions) {
            MethodExecution context = proceedWrapExecution(methodExecution.getMethod(),
                    methodExecution.getMethodArgs());
            if (context.hasThrowable()) {
                throw context.getTargetException();
            }

            Object firstResult = methodExecution.getResult();
            Object lastResult = context.getResult();

            if (firstResult != null && Proxy.isProxyClass(firstResult.getClass())) {
                if (Proxy.getInvocationHandler(firstResult) instanceof AbstractStatementRetryInterceptor<?>) {
                    ((AbstractStatementRetryInterceptor<?>) Proxy.getInvocationHandler(firstResult))
                            .retryWith(lastResult);
                } else {
                    throw new UnsupportedOperationException("Unknown JDBC proxy: " + firstResult);
                }
            }
        }
    }
}

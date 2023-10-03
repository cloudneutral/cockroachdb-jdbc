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

import org.postgresql.util.PSQLState;
import org.slf4j.MDC;

import io.cockroachdb.jdbc.CockroachPreparedStatement;
import io.cockroachdb.jdbc.CockroachStatement;
import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.InvalidConnectionException;
import io.cockroachdb.jdbc.util.Assert;
import io.cockroachdb.jdbc.util.ExceptionUtils;
import io.cockroachdb.jdbc.util.ResourceSupplier;

/**
 * A dynamic proxy interceptor / invocation handler around java.sql.Connection. This interceptor
 * provides the main retry logic for all retryable SQL exception that may surface at commit time
 * or from any associated statements or result sets.
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
                    clearHistory();
                    return null;
                } catch (InvocationTargetException e) {
                    attempt = rollbackAndRetry(e.getTargetException(), method, attempt, startTime);
                }
            }
        } else if ("setAutoCommit".equals(method.getName())) {
            clearHistory();
            return proceedExecution(method, args);
        } else if ("rollback".equals(method.getName())) {
            clearHistory();
            return proceedExecution(method, args);
        } else if ("close".equals(method.getName())) {
            clearHistory();
            return proceedExecution(method, args);
        } else if ("prepareStatement".equals(method.getName())) {
            MethodExecution context = proceedWrapExecution(method, args);
            if (context.hasThrowable()) {
                throw context.getTargetException();
            }
            addMethodExecution(context);

            CockroachPreparedStatement cockroachPreparedStatement
                    = new CockroachPreparedStatement((PreparedStatement) context.getResult());
            PreparedStatement preparedStatementRetryProxy
                    = PreparedStatementRetryInterceptor.proxy(cockroachPreparedStatement, this);
            context.setResult(preparedStatementRetryProxy);
            return preparedStatementRetryProxy;
        } else if ("createStatement".equals(method.getName())) {
            MethodExecution context = proceedWrapExecution(method, args);
            if (context.hasThrowable()) {
                throw context.getTargetException();
            }
            addMethodExecution(context);

            CockroachStatement cockroachStatement
                    = new CockroachStatement((Statement) context.getResult(), connectionSettings);
            Statement statementRetryProxy
                    = StatementRetryInterceptor.proxy(cockroachStatement, this);
            context.setResult(statementRetryProxy);
            return statementRetryProxy;
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
        addMethodExecution(context);
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
                // Unless it's a retryable error (which may include connection error) we can't continue
                if (!retryStrategy.isConnectionError(ex)) {
                    throw ex.initCause(rootCauseException);
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

    private void openDelegate(int attempt) throws SQLException {
        logger.debug("Opening new connection for attempt [{}]", attempt);
        Connection newDelegate = connectionSupplier.get();
        if (newDelegate.getAutoCommit()) {
            throw new InvalidConnectionException("Connection is in auto-commit mode",
                    PSQLState.UNEXPECTED_ERROR);
        }
        if (!newDelegate.isValid(10)) {
            throw new InvalidConnectionException("Connection is invalid",
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
        for (MethodExecution methodExecution : methodExecutions) {
            MethodExecution context = proceedWrapExecution(methodExecution.getMethod(),
                    methodExecution.getMethodArgs());
            if (context.hasThrowable()) {
                throw context.getTargetException();
            }

            Object firstResult = methodExecution.getResult();
            Object lastResult = context.getResult();

            if (firstResult != null && Proxy.isProxyClass(firstResult.getClass())) {
                if (Proxy.getInvocationHandler(firstResult) instanceof PreparedStatementRetryInterceptor) {
                    PreparedStatementRetryInterceptor firstProxy =
                            (PreparedStatementRetryInterceptor) Proxy.getInvocationHandler(firstResult);
                    firstProxy.retry((PreparedStatement) lastResult);
                } else if (Proxy.getInvocationHandler(firstResult) instanceof StatementRetryInterceptor) {
                    StatementRetryInterceptor firstProxy =
                            (StatementRetryInterceptor) Proxy.getInvocationHandler(firstResult);
                    firstProxy.retry((Statement) lastResult);
                } else {
                    throw new UnsupportedOperationException("Unknown JDBC proxy: " + firstResult);
                }
            }
        }
    }
}

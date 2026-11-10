package io.cockroachdb.jdbc.retry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Base class for {@code java.sql.Statement} and {@code java.sql.PreparedStatement} retry interceptors.
 * <p>
 * A statement may outlive the transaction it was created in (or be created in auto-commit mode
 * and later used in an explicit transaction). To be able to recreate such statements on a new
 * connection delegate during a retry, each interceptor remembers how it was created and keeps
 * a compacted history of the statement state that carries over between transactions:
 * statement settings, current parameter bindings and pending batch entries. Executions and
 * results from previous transactions are discarded at each transaction boundary.
 *
 * @param <T> the statement type
 * @author Kai Niemi
 */
public abstract class AbstractStatementRetryInterceptor<T extends Statement> extends AbstractRetryInterceptor<T> {
    protected final ConnectionRetryInterceptor connectionRetryInterceptor;

    private Method creationMethod;

    private Object[] creationArgs;

    private boolean createdInTransaction;

    private boolean closed;

    protected AbstractStatementRetryInterceptor(T delegate, ConnectionRetryInterceptor connectionRetryInterceptor) {
        super(delegate);
        this.connectionRetryInterceptor = connectionRetryInterceptor;
        setMethodTraceLogger(connectionRetryInterceptor.getConnectionSettings().getMethodTraceLogger());
    }

    void setCreation(Method creationMethod, Object[] creationArgs, boolean createdInTransaction) {
        this.creationMethod = creationMethod;
        this.creationArgs = creationArgs;
        this.createdInTransaction = createdInTransaction;
    }

    Method getCreationMethod() {
        return creationMethod;
    }

    Object[] getCreationArgs() {
        return creationArgs;
    }

    boolean isCreatedInTransaction() {
        return createdInTransaction;
    }

    boolean isClosed() {
        return closed;
    }

    /**
     * Invoked by the connection interceptor at transaction boundaries (commit, rollback or
     * change of auto-commit mode).
     */
    void endTransaction() {
        compactHistory();
        createdInTransaction = false;
    }

    @SuppressWarnings("unchecked")
    void retryWith(Object delegate) throws Throwable {
        retry((T) delegate);
    }

    @Override
    protected String connectionInfo() {
        return connectionRetryInterceptor.connectionInfo();
    }

    protected abstract ResultSet wrapResultSet(ResultSet resultSet);

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("toString".equals(method.getName())
                || "isWrapperFor".equals(method.getName())
                || "unwrap".equals(method.getName())
                || "hashCode".equals(method.getName())) {
            return proceed(method, args);
        }

        final Instant startTime = Instant.now();

        for (int attempt = 1; ; attempt++) { // Limit defined by retry strategy
            try {
                MethodExecution context = proceedWrapExecution(method, args);
                if (context.hasThrowable()) {
                    throw context.getThrowable();
                }
                record(context);

                if (context.getResult() instanceof ResultSet) {
                    ResultSet resultSetRetryProxy = ResultSetRetryInterceptor.proxy(
                            wrapResultSet((ResultSet) context.getResult()), connectionRetryInterceptor);
                    context.setResult(resultSetRetryProxy);
                }

                if ("close".equals(method.getName())) {
                    closed = true;
                    connectionRetryInterceptor.statementClosed(this);
                }

                return context.getResult();
            } catch (InvocationTargetException e) {
                attempt = connectionRetryInterceptor.rollbackAndRetry(e.getTargetException(), method, attempt,
                        startTime);
            }
        }
    }

    private void record(MethodExecution context) throws SQLException {
        addMethodExecution(context);
        // In auto-commit mode, each execution is its own transaction so only retain the
        // statement state needed to recreate it if later used in an explicit transaction.
        if (context.getMethod().getName().startsWith("execute") && connectionRetryInterceptor.isAutoCommit()) {
            compactHistory();
        }
    }

    /**
     * Reduce the recorded history to the statement state that survives a transaction boundary.
     */
    protected void compactHistory() {
        Map<String, MethodExecution> settings = new LinkedHashMap<>();
        Map<Integer, MethodExecution> parameters = new TreeMap<>();
        List<MethodExecution> pendingBatch = new ArrayList<>();
        MethodExecution closeOnCompletion = null;

        for (MethodExecution execution : getMethodExecutions()) {
            Method method = execution.getMethod();
            String name = method.getName();
            Object[] args = execution.getMethodArgs();

            if ("clearParameters".equals(name)) {
                parameters.clear();
            } else if ("addBatch".equals(name)) {
                if (method.getParameterCount() == 0) {
                    // Parameter bindings at the time of addBatch()
                    pendingBatch.addAll(parameters.values());
                }
                pendingBatch.add(execution);
            } else if ("executeBatch".equals(name)
                    || "executeLargeBatch".equals(name)
                    || "clearBatch".equals(name)) {
                pendingBatch.clear();
            } else if (isParameterSetter(method)) {
                parameters.put((Integer) args[0], execution);
            } else if (isStatementSetting(method)) {
                settings.put(name, execution);
            } else if ("closeOnCompletion".equals(name)) {
                closeOnCompletion = execution;
            }
            // Anything else (executions, getters, result sets) is transaction scoped
        }

        List<MethodExecution> compacted = new ArrayList<>(settings.values());
        compacted.addAll(pendingBatch);
        compacted.addAll(parameters.values());
        if (closeOnCompletion != null) {
            compacted.add(closeOnCompletion);
        }

        replaceMethodExecutions(compacted);
    }

    private static boolean isParameterSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterCount() >= 2
                && method.getParameterTypes()[0] == int.class;
    }

    private static boolean isStatementSetting(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterCount() == 1;
    }

    @Override
    protected void doRetry(Iterable<MethodExecution> methodExecutions)
            throws Throwable {
        for (MethodExecution methodExecution : methodExecutions) {
            MethodExecution lastExecutionResult
                    = proceedWrapExecution(methodExecution.getMethod(), methodExecution.getMethodArgs());
            if (lastExecutionResult.hasThrowable()) {
                throw lastExecutionResult.getTargetException();
            }
            Object firstResult = methodExecution.getResult();
            Object lastResult = lastExecutionResult.getResult();

            if (firstResult != null && Proxy.isProxyClass(firstResult.getClass())) {
                // Keep the recorded result pointing at the proxy handed to the application, so that
                // the result set is re-pointed and its reads re-verified on any subsequent retry.
                InvocationHandler firstHandler = Proxy.getInvocationHandler(firstResult);
                ResultSetRetryInterceptor firstProxy = (ResultSetRetryInterceptor) firstHandler;
                firstProxy.retry((ResultSet) lastResult);
            } else {
                verifyObservedResult(methodExecution.getMethod(), firstResult, lastResult);
                methodExecution.setResult(lastResult);
            }
        }
    }

    /**
     * Results observed by the application from statement executions (update counts, batch
     * update counts, execute() flags) must be identical when replayed, otherwise the application
     * may have acted on outcomes that no longer hold (like an optimistic lock version check).
     * Result sets are verified separately by checksum.
     */
    private static void verifyObservedResult(Method method, Object firstResult, Object lastResult)
            throws ConcurrentUpdateException {
        String name = method.getName();
        boolean observed = name.startsWith("execute")
                || "getUpdateCount".equals(name)
                || "getLargeUpdateCount".equals(name)
                || "getMoreResults".equals(name);
        if (observed && !Objects.deepEquals(firstResult, lastResult)) {
            throw new ConcurrentUpdateException(
                    "The transaction could not serialize due to a concurrent update (result of "
                            + name + "() changed from " + toString(firstResult) + " to " + toString(lastResult) + ")");
        }
    }

    private static String toString(Object result) {
        if (result instanceof int[]) {
            return Arrays.toString((int[]) result);
        }
        if (result instanceof long[]) {
            return Arrays.toString((long[]) result);
        }
        return String.valueOf(result);
    }
}

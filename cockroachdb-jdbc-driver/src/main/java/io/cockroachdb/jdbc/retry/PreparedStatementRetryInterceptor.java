package io.cockroachdb.jdbc.retry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

/**
 * An interceptor for {@code java.sql.PreparedStatement} with retry capability.
 *
 * @author Kai Niemi
 */
public class PreparedStatementRetryInterceptor extends AbstractRetryInterceptor<PreparedStatement>
        implements InvocationHandler {
    public static PreparedStatement proxy(PreparedStatement delegate,
                                          ConnectionRetryInterceptor connectionInterceptor) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatementRetryInterceptor.class.getClassLoader(),
                new Class[] {PreparedStatement.class},
                new PreparedStatementRetryInterceptor(delegate, connectionInterceptor));
    }

    private final ConnectionRetryInterceptor connectionRetryInterceptor;

    protected PreparedStatementRetryInterceptor(PreparedStatement delegate,
                                                ConnectionRetryInterceptor connectionRetryInterceptor) {
        super(delegate);
        this.connectionRetryInterceptor = connectionRetryInterceptor;
        setMethodTraceLogger(connectionRetryInterceptor.getConnectionSettings().getMethodTraceLogger());
    }

    @Override
    protected String connectionInfo() {
        return connectionRetryInterceptor.connectionInfo();
    }

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
                addMethodExecution(context);

                if (context.getResult() instanceof ResultSet) {
                    ResultSet resultSet = (ResultSet) context.getResult();
                    ResultSet resultSetRetryProxy = ResultSetRetryInterceptor.proxy(resultSet,
                            connectionRetryInterceptor);
                    context.setResult(resultSetRetryProxy);
                }

                return context.getResult();
            } catch (InvocationTargetException e) {
                attempt = connectionRetryInterceptor.rollbackAndRetry(e.getTargetException(), method, attempt,
                        startTime);
            }
        }
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

            methodExecution.setResult(lastResult);

            if (firstResult != null && Proxy.isProxyClass(firstResult.getClass())) {
                InvocationHandler firstHandler = Proxy.getInvocationHandler(firstResult);
                ResultSetRetryInterceptor firstProxy = (ResultSetRetryInterceptor) firstHandler;
                firstProxy.retry((ResultSet) lastResult);
            }
        }
    }
}

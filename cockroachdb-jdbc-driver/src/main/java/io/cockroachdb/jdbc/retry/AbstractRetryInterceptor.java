package io.cockroachdb.jdbc.retry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.cockroachdb.jdbc.util.Assert;

public abstract class AbstractRetryInterceptor<T> extends AbstractInterceptor<T> {
    private MethodTraceLogger methodTraceLogger;

    private final List<MethodExecution> methodExecutions = new ArrayList<>();

    public AbstractRetryInterceptor(T delegate) {
        super(delegate);
    }

    public void setMethodTraceLogger(MethodTraceLogger methodTraceLogger) {
        this.methodTraceLogger = methodTraceLogger;
    }

    protected void addMethodExecution(MethodExecution methodExecution) {
        Assert.notNull(methodExecution, "methodExecution is null");
        methodExecutions.add(methodExecution);
    }

    protected final void clearHistory() {
        methodExecutions.clear();
    }

    protected MethodExecution proceedWrapExecution(Method method, Object[] args) {
        Assert.notNull(method, "method is null");

        Instant callTime = Instant.now();
        Duration executionTime;
        Throwable ex = null;
        Object result = null;

        long no = 0;
        try {
            if (methodTraceLogger != null) {
                no = methodTraceLogger.before(connectionInfo(), getDelegate(), method, args);
            }
            result = proceed(method, args);
        } catch (Throwable e) {
            ex = e;
        } finally {
            executionTime = Duration.between(callTime, Instant.now());
            if (methodTraceLogger != null) {
                methodTraceLogger.after(no, connectionInfo(), getDelegate(), method, args, executionTime, ex);
            }
        }

        return MethodExecution.builder()
                .withTarget(getDelegate())
                .withMethod(method)
                .withMethodArgs(args)
                .withResult(result)
                .withThrowable(ex)
                .withExecutionTime(executionTime)
                .withConnectionInfo(connectionInfo())
                .build();
    }

    protected Object proceedExecution(Method method, Object[] args) throws Throwable {
        Assert.notNull(method, "method is null");

        Instant callTime = Instant.now();
        Throwable ex = null;

        long no = 0;
        try {
            if (methodTraceLogger != null) {
                no = methodTraceLogger.before(connectionInfo(), getDelegate(), method, args);
            }
            return proceed(method, args);
        } catch (Throwable e) {
            ex = e;
            throw e;
        } finally {
            if (methodTraceLogger != null) {
                methodTraceLogger.after(no, connectionInfo(),
                        getDelegate(), method, args, Duration.between(callTime, Instant.now()), ex);
            }
        }
    }

    protected final void retry(T delegate) throws Throwable {
        setDelegate(delegate);
        if (logger.isDebugEnabled()) {
            logger.debug("Repeating [{}] method executions for delegate [{}]: {}",
                    methodExecutions.size(), getDelegate().toString(), toStringCallstack());
        }
        doRetry(Collections.unmodifiableList(methodExecutions));
    }

    protected String toStringCallstack() {
        StringBuilder results = new StringBuilder();
        int methodCount = 0;

        for (MethodExecution methodExecution : methodExecutions) {
            results.append("\n\t[");
            results.append(methodCount);
            results.append("] ");
            results.append(methodExecution.getMethod().toGenericString());
            methodCount++;
            if (methodCount > 5) {
                results.append("\n\t(truncated at 5 but there are ")
                        .append(methodExecutions.size()).append(" methods queued in total)");
                break;
            }
        }
        return results.toString();
    }

    protected abstract String connectionInfo();

    protected abstract void doRetry(Iterable<MethodExecution> methodExecutions)
            throws Throwable;
}

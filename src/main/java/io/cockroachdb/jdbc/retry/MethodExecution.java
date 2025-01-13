package io.cockroachdb.jdbc.retry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;

/**
 * Execution context for JDBC method calls.
 *
 * @author Kai Niemi
 */
public class MethodExecution {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MethodExecution instance = new MethodExecution();

        public Builder withTarget(Object target) {
            instance.target = target;
            return this;
        }

        public Builder withMethod(Method method) {
            instance.method = method;
            return this;
        }

        public Builder withMethodArgs(Object[] methodArgs) {
            instance.methodArgs = methodArgs;
            return this;
        }

        public Builder withResult(Object result) {
            instance.result = result;
            return this;
        }

        public Builder withThrowable(Throwable thrown) {
            instance.throwable = thrown;
            return this;
        }

        public Builder withExecutionTime(Duration executionTime) {
            instance.executionTime = executionTime;
            return this;
        }

        public Builder withConnectionInfo(String connectionInfo) {
            instance.connectionInfo = connectionInfo;
            return this;
        }

        public MethodExecution build() {
            return instance;
        }
    }

    private Object target;

    private Method method;

    private Object[] methodArgs;

    private Object result;

    private Throwable throwable;

    private Duration executionTime;

    private String connectionInfo;

    public boolean hasThrowable() {
        return throwable != null;
    }

    public Object getTarget() {
        return target;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Object[] getMethodArgs() {
        return methodArgs;
    }

    public void setMethodArgs(Object[] methodArgs) {
        this.methodArgs = methodArgs;
    }

    public Object getResult() {
        return getResult(Object.class);
    }

    public <T> T getResult(Class<T> type) {
        return type.cast(result);
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Throwable getTargetException() {
        if (throwable instanceof InvocationTargetException) {
            return ((InvocationTargetException) throwable)
                    .getTargetException();
        }
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Duration executionTime) {
        this.executionTime = executionTime;
    }

    public String getConnectionInfo() {
        return connectionInfo;
    }

    public void setConnectionInfo(String connectionInfo) {
        this.connectionInfo = connectionInfo;
    }

    public String getStatus() {
        return (throwable == null ? "SUCCESS" : "FAIL");
    }

    private String getClassName(Object object) {
        return object != null ? object.getClass().getName() : "null";
    }

    @Override
    public String toString() {
        return "MethodExecution{" +
                "\n\ttarget=" + getTarget() +
                "\n\ttargetClass=" + getClassName(getTarget()) +
                "\n\tmethod=" + getMethod() +
                "\n\tmethodArgs=" + Arrays.toString(getMethodArgs()) +
                "\n\tresult=" + getResult() +
                "\n\tresultClass=" + getClassName(getResult()) +
                "\n\tthrowable=" + getThrowable() +
                "\n\tstatus=" + getStatus() +
                "\n\texecutionTime=" + getExecutionTime() +
                "\n\tconnectionInfo='" + getConnectionInfo() + '\'' +
                '}';
    }
}

package io.cockroachdb.jdbc.retry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cockroachdb.jdbc.util.Assert;

public abstract class AbstractInterceptor<T> implements InvocationHandler {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private T delegate;

    public AbstractInterceptor(T delegate) {
        Assert.notNull(delegate, "delegate is null");
        this.delegate = delegate;
    }

    public T getDelegate() {
        return delegate;
    }

    public void setDelegate(T delegate) {
        Assert.notNull(delegate, "delegate is null");
        this.delegate = delegate;
    }

    protected final Object proceed(Method method, Object[] args) throws Throwable {
        return method.invoke(getDelegate(), args);
    }

    @Override
    public String toString() {
        return "AbstractInterceptor{" +
                "delegate=" + delegate +
                '}';
    }
}

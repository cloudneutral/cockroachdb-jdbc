package io.cockroachdb.jdbc.util;

import java.sql.SQLException;
import java.sql.Wrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WrapperSupport<D> implements Wrapper {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private D delegate;

    protected WrapperSupport(D delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is null");
        }
        this.delegate = delegate;
    }

    protected D getDelegate() throws SQLException {
        return delegate;
    }

    protected void setDelegate(D delegate) {
        this.delegate = delegate;
    }

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(delegate.getClass())) {
            return iface.cast(delegate);
        } else if (Wrapper.class.isAssignableFrom(delegate.getClass())) {
            return iface.cast(((Wrapper) delegate).unwrap(iface));
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(delegate.getClass())) {
            return true;
        } else if (Wrapper.class.isAssignableFrom(delegate.getClass())) {
            return ((Wrapper) delegate).isWrapperFor(iface);
        }
        return false;
    }

    @Override
    public String toString() {
        return "WrapperSupport{" +
                "delegate=" + delegate +
                '}';
    }
}

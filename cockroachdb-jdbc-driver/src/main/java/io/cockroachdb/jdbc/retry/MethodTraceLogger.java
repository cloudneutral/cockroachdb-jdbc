package io.cockroachdb.jdbc.retry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import io.cockroachdb.jdbc.util.ExceptionUtils;
import io.cockroachdb.jdbc.util.TraceUtils;

public class MethodTraceLogger {
    private static final AtomicLong sequenceNumber = new AtomicLong(0);

    public static MethodTraceLogger createInstance(Logger logger) {
        return new MethodTraceLogger(logger);
    }

    private final Logger logger;

    private boolean masked;

    private MethodTraceLogger(Logger logger) {
        this.logger = logger;
    }

    public MethodTraceLogger setMasked(boolean masked) {
        this.masked = masked;
        return this;
    }

    public long before(String connectionId, Object target, Method method, Object[] args) {
        if (!logger.isTraceEnabled()) {
            return 0;
        }
        long no = sequenceNumber.incrementAndGet();

        StringBuilder sb = new StringBuilder();
        sb.append(">> before [");
        sb.append(no);
        sb.append("]");

        sb.append("[conn=");
        sb.append(connectionId);
        sb.append("]");

        sb.append(" ");
        sb.append(target.getClass().getName());
        sb.append("#");
        sb.append(method.getName());
        sb.append("(");
        sb.append(TraceUtils.methodArgsToString(args, masked));
        sb.append(")");

        logger.trace(sb.toString());

        return no;
    }

    public void after(long no, String connectionId,
                      Object target, Method method, Object[] args, Duration callDuration, Throwable throwable) {
        if (!logger.isTraceEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<< after [");
        sb.append(no);
        sb.append("]");

        sb.append("[");
        sb.append(throwable == null ? "success" : "fail");
        sb.append("]");

        sb.append("[");
        sb.append(callDuration);
        sb.append("ms]");

        sb.append("[conn=");
        sb.append(connectionId);
        sb.append("]");

        if (throwable != null) {
            if (throwable instanceof InvocationTargetException) {
                Throwable targetException = ((InvocationTargetException) throwable).getTargetException();
                sb.append("[error=");
                sb.append(targetException.getMessage());
                sb.append("]");
                if (targetException instanceof SQLException) {
                    sb.append("[sqlState=");
                    sb.append(((SQLException) targetException).getSQLState());
                    sb.append("]");
                }
            } else {
                sb.append("[error=");
                sb.append(ExceptionUtils.getMostSpecificCause(throwable).getMessage());
                sb.append("]");
            }
        }

        sb.append(" ");
        sb.append(target.getClass().getName());
        sb.append("#");
        sb.append(method.getName());
        sb.append("(");
        sb.append(TraceUtils.methodArgsToString(args, masked));
        sb.append(")");

        logger.trace(sb.toString());
    }
}

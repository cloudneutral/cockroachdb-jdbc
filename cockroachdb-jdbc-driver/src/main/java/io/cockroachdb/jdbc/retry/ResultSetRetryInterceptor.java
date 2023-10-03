package io.cockroachdb.jdbc.retry;

import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.UUID;

import io.cockroachdb.jdbc.util.Checksum;
import io.cockroachdb.jdbc.util.StreamUtils;

public class ResultSetRetryInterceptor extends AbstractRetryInterceptor<ResultSet> implements InvocationHandler {
    public static ResultSet proxy(ResultSet resultSet, ConnectionRetryInterceptor connectionInterceptor) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSetRetryInterceptor.class.getClassLoader(),
                new Class[] {ResultSet.class},
                new ResultSetRetryInterceptor(resultSet, connectionInterceptor));
    }

    private final ConnectionRetryInterceptor connectionRetryInterceptor;

    private final Checksum firstChecksum = Checksum.sha256();

    protected ResultSetRetryInterceptor(ResultSet delegate,
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

        MethodExecution context = proceedWrapExecution(method, args);
        if (context.hasThrowable()) {
            throw context.getTargetException();
        }
        addMethodExecution(context);
        // We need to compute the checksum while the result is still valid
        return updateChecksum(context.getResult(), firstChecksum);
    }

    @Override
    protected void doRetry(Iterable<MethodExecution> methodExecutions) throws Throwable {
        Checksum lastChecksum = Checksum.sha256();

        for (MethodExecution methodExecution : methodExecutions) {
            MethodExecution lastExecution = proceedWrapExecution(methodExecution.getMethod(),
                    methodExecution.getMethodArgs());
            if (lastExecution.hasThrowable()) {
                throw lastExecution.getTargetException();
            }

            Object lastResult = lastExecution.getResult();

            methodExecution.setResult(lastResult);

            Object rv = updateChecksum(lastResult, lastChecksum);
            if (rv instanceof InputStream) {
                StreamUtils.drain((InputStream) rv);
            } else if (rv instanceof Reader) {
                StreamUtils.drain((Reader) rv);
            }
        }

        byte[] firstDigest = firstChecksum.toDigest();
        byte[] lastDigest = lastChecksum.toDigest();

        if (!Arrays.equals(firstDigest, lastDigest)) {
            throw new ConcurrentUpdateException(
                    "The transaction could not serialize due to a concurrent update (checksum failure)");
        }
    }

    private <T> T updateChecksum(T obj, Checksum checksum) {
        if (obj instanceof String) {
            checksum.update(((String) obj).getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof BigDecimal) {
            checksum.update(((BigDecimal) obj).toPlainString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof Boolean) {
            checksum.update(((Boolean) obj).toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof Integer) {
            checksum.update(((Integer) obj).toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof Long) {
            checksum.update(((Long) obj).toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof Float) {
            checksum.update(obj.toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof Double) {
            checksum.update(obj.toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof byte[]) {
            checksum.update((byte[]) obj);
        } else if (obj instanceof java.sql.Date) {
            checksum.update(((java.sql.Date) obj).toLocalDate().toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof java.sql.Time) {
            checksum.update(((java.sql.Time) obj).toLocalTime().toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof java.sql.Timestamp) {
            checksum.update(
                    ((java.sql.Timestamp) obj).toLocalDateTime().toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj instanceof InputStream) {
            InputStream in = (InputStream) obj;
            //noinspection unchecked
            return (T) new FilterInputStream(in) {
                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b != -1) {
                        checksum.update(ByteBuffer.allocate(4).putInt(b).array());
                    }
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    len = in.read(b, off, len);
                    if (len != -1) {
                        checksum.update(b, off, len);
                    }
                    return len;
                }
            };
        } else if (obj instanceof Reader) {
            Reader in = (Reader) obj;
            //noinspection unchecked
            return (T) new FilterReader(in) {
                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b != -1) {
                        checksum.update(ByteBuffer.allocate(4).putInt(b).array());
                    }
                    return b;
                }

                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    len = super.read(cbuf, off, len);
                    if (len != -1) {
                        checksum.update(new String(cbuf, off, len).getBytes(StandardCharsets.UTF_8));
                    }
                    return len;
                }
            };
        } else if (obj instanceof ResultSetMetaData) {
            // Ignore
        } else if (obj instanceof UUID) {
            checksum.update(obj.toString().getBytes(StandardCharsets.UTF_8));
        } else if (obj != null) {
            // Unsupported type - use non-deterministic value to force checksum failure on retry
            checksum.update(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            if (logger.isWarnEnabled()) {
                logger.warn("Unable to compute checksum for JDBC type {} - using non-deterministic value to force "
                                + "checksum failure on a potential retry",
                        obj.getClass().getName());
            }
        }

        return obj;
    }
}

package io.cockroachdb.jdbc.util;

import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Method tracing debug utils.
 *
 * @author Kai Niemi
 */
public abstract class TraceUtils {
    private TraceUtils() {
    }

    private static final int PARAMETER_MAX_LENGTH = 50;

    public static String methodArgsToString(Object[] args, boolean masked) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (args.length == 1) {
            Object arg = args[0];
            String param = truncateParameter(parameterAsString(arg, masked));
            if (arg instanceof String) {
                sb.append("\"");
                sb.append(param);
                sb.append("\"");
            } else {
                sb.append(param);
            }
        } else {
            IntStream.range(0, args.length).forEach(i -> {
                Object arg = args[i];
                boolean lastArg = i == args.length - 1;
                String displayParam = truncateParameter(parameterAsString(arg, masked));
                if (arg instanceof String) {
                    sb.append("\"");
                    sb.append(displayParam);
                    sb.append("\"");
                } else {
                    sb.append(displayParam);
                }
                if (!lastArg) {
                    sb.append(",");
                }
            });
        }
        return sb.toString();
    }

    public static String parameterAsString(Object arg, boolean masked) {
        if (arg == null) {
            return "null";
        }
        String v = Objects.toString(arg);
        if (masked) {
            return v.replaceAll(".", "*");
        }
        return v;
    }

    public static String truncateParameter(String parameter) {
        if (parameter.length() <= PARAMETER_MAX_LENGTH) {
            return parameter;
        }
        return parameter.substring(0, PARAMETER_MAX_LENGTH - 3) + "...";
    }
}

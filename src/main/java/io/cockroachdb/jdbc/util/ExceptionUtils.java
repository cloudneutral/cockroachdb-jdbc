package io.cockroachdb.jdbc.util;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.EnumSet;

import org.postgresql.util.PSQLState;

/**
 * Exception helper functions.
 *
 * @author Kai Niemi
 */
public abstract class ExceptionUtils {
    private static EnumSet<PSQLState> PSQL_STATES = EnumSet.allOf(PSQLState.class);

    private ExceptionUtils() {
    }

    public static String toNestedString(SQLWarning sqlWarning) {
        StringBuilder sb = new StringBuilder();
        StringBuilder indent = new StringBuilder().append("  ");

        while (sqlWarning != null) {
            String state = sqlWarning.getSQLState();
            sb.append(indent)
                    .append(sqlWarning)
                    .append("\n").append(indent)
                    .append("SQL State: ").append(state).append(" (").append(toPSQLState(state)).append(")");
            sqlWarning = sqlWarning.getNextWarning();
            if (sqlWarning != null) {
                indent.append("  ");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public static String toNestedString(SQLException ex) {
        StringBuilder sb = new StringBuilder();
        StringBuilder indent = new StringBuilder().append("  ");

        while (ex != null) {
            String state = ex.getSQLState();
            sb.append(indent)
                    .append(ex)
                    .append("\n")
                    .append(indent)
                    .append("SQL State: ")
                    .append(state)
                    .append(" (")
                    .append(toPSQLState(state))
                    .append(")");
            ex = ex.getNextException();
            if (ex != null) {
                indent.append("  ");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public static PSQLState toPSQLState(String state) {
        return PSQL_STATES.stream()
                .filter(s -> s.getState().equals(state))
                .findFirst().orElse(PSQLState.UNKNOWN_STATE);
    }

    public static Throwable getRootCause(Throwable original) {
        if (original == null) {
            return null;
        }
        Throwable rootCause = null;
        Throwable cause = original.getCause();
        while (cause != null && cause != rootCause) {
            rootCause = cause;
            cause = cause.getCause();
        }
        return rootCause;
    }

    public static Throwable getMostSpecificCause(Throwable original) {
        Throwable rootCause = getRootCause(original);
        return (rootCause != null ? rootCause : original);
    }
}

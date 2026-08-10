package com.xfwang.govdata.datasource.infrastructure.connector.support;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 连通测试异常分类器。
 */
public final class ConnectionFailureClassifier {

    private static final Set<Integer> AUTH_ERROR_CODES = Set.of(1017, 1045, 18456, 194);
    private static final Set<Integer> DATABASE_ERROR_CODES = Set.of(81, 1049);

    private ConnectionFailureClassifier() {
    }

    public static ConnectionTestReason classify(Throwable failure) {
        for (Throwable current = failure; current != null; current = nextCause(current)) {
            if (current instanceof ClassNotFoundException) {
                return ConnectionTestReason.DRIVER_UNAVAILABLE;
            }
            if (current instanceof SSLException) {
                return ConnectionTestReason.TLS_HANDSHAKE_FAILED;
            }
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException
                || current.getClass().getSimpleName().contains("Timeout")) {
                return ConnectionTestReason.CONNECTION_TIMEOUT;
            }
            if (current instanceof ConnectException) {
                return ConnectionTestReason.CONNECTION_REFUSED;
            }
            if (current instanceof UnknownHostException) {
                return ConnectionTestReason.HOST_UNREACHABLE;
            }
            if (current instanceof ConnectionFailureException connectionFailure) {
                return connectionFailure.reason();
            }
            if (current instanceof SQLException sqlException) {
                ConnectionTestReason reason = classifySqlException(sqlException);
                if (reason != null) {
                    return reason;
                }
            }
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                ConnectionTestReason reason = classifyMessage(message);
                if (reason != null) {
                    return reason;
                }
            }
        }
        return ConnectionTestReason.CONNECTION_FAILED;
    }

    private static ConnectionTestReason classifySqlException(SQLException sqlException) {
        int vendorCode = Math.abs(sqlException.getErrorCode());
        if (AUTH_ERROR_CODES.contains(vendorCode)) {
            return ConnectionTestReason.AUTHENTICATION_FAILED;
        }
        if (DATABASE_ERROR_CODES.contains(vendorCode)) {
            return ConnectionTestReason.DATABASE_NOT_FOUND;
        }
        String sqlState = sqlException.getSQLState();
        if (sqlState != null) {
            if (sqlState.startsWith("28")) {
                return ConnectionTestReason.AUTHENTICATION_FAILED;
            }
            if (sqlState.startsWith("3D") || sqlState.startsWith("42")) {
                return ConnectionTestReason.DATABASE_NOT_FOUND;
            }
            if (sqlState.startsWith("08")) {
                return ConnectionTestReason.CONNECTION_REFUSED;
            }
        }
        return null;
    }

    private static ConnectionTestReason classifyMessage(String rawMessage) {
        String message = rawMessage.toLowerCase(Locale.ROOT);
        if (message.contains("access denied") || message.contains("password")
            || message.contains("authentication failed") || message.contains("login failed")) {
            return ConnectionTestReason.AUTHENTICATION_FAILED;
        }
        if (message.contains("unknown database") || message.contains("database does not exist")
            || message.contains("invalid schema") || message.contains("catalog not found")) {
            return ConnectionTestReason.DATABASE_NOT_FOUND;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return ConnectionTestReason.CONNECTION_TIMEOUT;
        }
        if (message.contains("connection refused") || message.contains("refused")) {
            return ConnectionTestReason.CONNECTION_REFUSED;
        }
        if (message.contains("unknown host") || message.contains("could not resolve host")) {
            return ConnectionTestReason.HOST_UNREACHABLE;
        }
        if (message.contains("ssl") || message.contains("tls") || message.contains("handshake")) {
            return ConnectionTestReason.TLS_HANDSHAKE_FAILED;
        }
        if (message.contains("endpoint") || message.contains("invalid") || message.contains("unmapped config")) {
            return ConnectionTestReason.ENDPOINT_INVALID;
        }
        return null;
    }

    private static Throwable nextCause(Throwable current) {
        return current.getCause() == current ? null : current.getCause();
    }
}

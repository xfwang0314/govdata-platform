package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureClassifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ConnectionFailureClassifierTest {

    @Test
    void classifiesStableConnectionFailureReasonsWithoutExposingDriverMessages() {
        assertThat(ConnectionFailureClassifier.classify(new SQLException("password is incorrect", "28000", 194)))
            .isEqualTo(ConnectionTestReason.AUTHENTICATION_FAILED);
        assertThat(ConnectionFailureClassifier.classify(new SQLException("Unknown database", "3D000", 81)))
            .isEqualTo(ConnectionTestReason.DATABASE_NOT_FOUND);
        assertThat(ConnectionFailureClassifier.classify(new SocketTimeoutException("timed out")))
            .isEqualTo(ConnectionTestReason.CONNECTION_TIMEOUT);
        assertThat(ConnectionFailureClassifier.classify(new ConnectException("Connection refused")))
            .isEqualTo(ConnectionTestReason.CONNECTION_REFUSED);
        assertThat(ConnectionFailureClassifier.classify(
            new SQLException("connection error", "08001", 0, new ConnectException("Connection refused"))))
            .isEqualTo(ConnectionTestReason.CONNECTION_REFUSED);
        assertThat(ConnectionFailureClassifier.classify(new UnknownHostException("private.example")))
            .isEqualTo(ConnectionTestReason.HOST_UNREACHABLE);
        assertThat(ConnectionFailureClassifier.classify(new SSLHandshakeException("certificate detail")))
            .isEqualTo(ConnectionTestReason.TLS_HANDSHAKE_FAILED);
        assertThat(ConnectionFailureClassifier.classify(new ClassNotFoundException("driver")))
            .isEqualTo(ConnectionTestReason.DRIVER_UNAVAILABLE);
        assertThat(ConnectionFailureClassifier.classify(
            new IllegalArgumentException("Unknown and unmapped config properties: [username]")))
            .isEqualTo(ConnectionTestReason.ENDPOINT_INVALID);
        assertThat(ConnectionFailureClassifier.classify(new IllegalStateException("unexpected secret text")))
            .isEqualTo(ConnectionTestReason.CONNECTION_FAILED);
    }
}

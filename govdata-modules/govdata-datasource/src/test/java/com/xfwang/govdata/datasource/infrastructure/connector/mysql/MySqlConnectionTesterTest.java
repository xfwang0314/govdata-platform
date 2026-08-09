package com.xfwang.govdata.datasource.infrastructure.connector.mysql;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class MySqlConnectionTesterTest {

    private final MySqlConnectionTester tester = new MySqlConnectionTester(java.util.List.of());

    @Test
    void rejectsBlankJdbcUrlBeforeResolvingSecret() {
        ConnectionTestResult result = tester.test(ConnectionTestRequest.builder()
            .connectorCode("mysql")
            .connectorVersion("8")
            .endpointConfig(Map.of("jdbcUrl", ""))
            .secretRef("secret:sec_mysql")
            .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("ENDPOINT_INVALID");
    }

    @Test
    void reportsUnavailableSecretWithoutLeakingReference() {
        ConnectionTestResult result = tester.test(ConnectionTestRequest.builder()
            .connectorCode("mysql")
            .connectorVersion("8")
            .endpointConfig(Map.of("jdbcUrl", "jdbc:mysql://127.0.0.1:3306/govdata"))
            .secretRef("vault:missing")
            .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(result.getMessage()).doesNotContain("vault:missing");
    }
}

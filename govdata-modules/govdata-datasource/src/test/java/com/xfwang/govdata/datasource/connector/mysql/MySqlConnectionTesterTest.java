package com.xfwang.govdata.datasource.connector.mysql;

import com.xfwang.govdata.datasource.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.connector.ConnectionTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class MySqlConnectionTesterTest {

    private final MySqlConnectionTester tester = new MySqlConnectionTester(java.util.List.of());

    @Test
    void rejectsArbitraryHostBeforeResolvingSecret() {
        ConnectionTestResult result = tester.test(ConnectionTestRequest.builder()
            .connectorCode("mysql")
            .connectorVersion("8")
            .endpointConfig(Map.of("host", "db.local/?allowLoadLocalInfile=true", "port", 3306,
                "database", "govdata"))
            .secretRef("env:DB_USER,DB_PASSWORD")
            .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("ENDPOINT_INVALID");
    }

    @Test
    void reportsUnavailableSecretWithoutLeakingReference() {
        ConnectionTestResult result = tester.test(ConnectionTestRequest.builder()
            .connectorCode("mysql")
            .connectorVersion("8")
            .endpointConfig(Map.of("host", "127.0.0.1", "port", 3306, "database", "govdata"))
            .secretRef("vault:missing")
            .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(result.getMessage()).doesNotContain("vault:missing");
    }
}

package com.xfwang.govdata.datasource.infrastructure.connector.clickhouse;

import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ClickHouseConnectionTesterTest {

    @Test
    void usesClickHouseV2TimeoutPropertyNames() {
        TestableClickHouseConnectionTester tester = new TestableClickHouseConnectionTester();

        Properties properties = tester.configuredProperties();

        assertThat(properties).containsEntry("connection_timeout", "5000")
            .containsEntry("socket_timeout", "5000")
            .doesNotContainKey("username")
            .doesNotContainKeys("connectTimeout", "socketTimeout");
    }

    @Test
    void exposesTheJdbcV2UrlFormRecommendedByTheDriver() {
        assertThat(new TestableClickHouseConnectionTester().descriptor().getConnectionPreview().getTemplate())
            .isEqualTo("jdbc:ch:{protocol}://{host}:{port}/{database}");
    }

    private static final class TestableClickHouseConnectionTester extends ClickHouseConnectionTester {
        private TestableClickHouseConnectionTester() {
            super(List.<SecretProvider>of());
        }

        private Properties configuredProperties() {
            Properties properties = new Properties();
            properties.setProperty("username", "compatibility-value");
            configure(properties, ConnectionTestRequest.builder().build());
            return properties;
        }
    }
}

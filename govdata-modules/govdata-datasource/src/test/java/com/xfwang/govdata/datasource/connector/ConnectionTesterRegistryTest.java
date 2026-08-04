package com.xfwang.govdata.datasource.connector;

import com.xfwang.govdata.datasource.DataSourceErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ConnectionTesterRegistryTest {

    @Test
    void selectsTesterByDeclaredCapability() {
        ConnectionTester mysql = new StubTester("mysql");
        ConnectionTester redis = new StubTester("redis");
        ConnectionTesterRegistry registry = new ConnectionTesterRegistry(List.of(mysql, redis));

        assertThat(registry.require("redis", "1")).isSameAs(redis);
    }

    @Test
    void rejectsUnsupportedConnectorWithStableErrorCode() {
        ConnectionTesterRegistry registry = new ConnectionTesterRegistry(List.of(new StubTester("mysql")));

        assertThatThrownBy(() -> registry.require("sftp", "1"))
            .isInstanceOfSatisfying(ServiceException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(DataSourceErrorCode.CONNECTOR_UNAVAILABLE.getCode()));
    }

    private record StubTester(String code) implements ConnectionTester {
        @Override
        public boolean supports(String connectorCode, String connectorVersion) {
            return code.equals(connectorCode);
        }

        @Override
        public ConnectionTestResult test(ConnectionTestRequest request) {
            return ConnectionTestResult.builder().success(true).build();
        }
    }
}

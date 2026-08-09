package com.xfwang.govdata.datasource.infrastructure.connector.jdbc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JDBC Connector 共享配置描述测试")
class JdbcConnectorProfileTest {

    @Test
    @DisplayName("预定义 Connector 描述符合契约")
    void shouldVerifyPredefinedProfiles() {
        assertThat(JdbcConnectorProfile.MYSQL.code()).isEqualTo("mysql");
        assertThat(JdbcConnectorProfile.MYSQL.version()).isEqualTo("1.0");
        assertThat(JdbcConnectorProfile.MYSQL.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");

        assertThat(JdbcConnectorProfile.ORACLE.code()).isEqualTo("oracle");
        assertThat(JdbcConnectorProfile.POSTGRESQL.code()).isEqualTo("postgresql");
        assertThat(JdbcConnectorProfile.CLICKHOUSE.code()).isEqualTo("clickhouse");
    }
}

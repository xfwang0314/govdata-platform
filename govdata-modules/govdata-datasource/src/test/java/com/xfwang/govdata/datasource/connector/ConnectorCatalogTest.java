package com.xfwang.govdata.datasource.connector;

import com.xfwang.govdata.datasource.connector.clickhouse.ClickHouseConnectionTester;
import com.xfwang.govdata.datasource.connector.doris.DorisConnectionTester;
import com.xfwang.govdata.datasource.connector.mysql.MySqlConnectionTester;
import com.xfwang.govdata.datasource.connector.oracle.OracleConnectionTester;
import com.xfwang.govdata.datasource.connector.redis.RedisConnectionTester;
import com.xfwang.govdata.datasource.connector.sftp.SftpConnectionTester;
import com.xfwang.platform.secret.api.SecretProvider;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ConnectorCatalogTest {

    private final List<ConnectorDescriptorProvider> providers = providers();
    private final ConnectorCatalog catalog = new ConnectorCatalog(providers);

    @Test
    void exposesExactlyTheSixFirstVersionConnectors() {
        assertThat(catalog.list())
            .extracting(ConnectorDescriptor::getConnectorCode)
            .containsExactly("clickhouse", "doris", "mysql", "oracle", "redis", "sftp");
        assertThat(catalog.list())
            .allSatisfy(descriptor -> {
                assertThat(descriptor.getConnectorVersion()).isEqualTo("1.0");
                assertThat(descriptor.getCapabilities()).contains("CONNECTION_TEST");
                assertThat(descriptor.getSecretKeys()).contains("username", "password");
            });
    }

    @Test
    void appliesConnectorDefaultsForPageSubmittedConfiguration() {
        Map<String, Object> mysql = endpoint("host", "127.0.0.1", "database", "demo");
        Map<String, Object> oracle = endpoint("host", "127.0.0.1", "serviceName", "ORCL");
        Map<String, Object> redis = endpoint("host", "127.0.0.1");
        Map<String, Object> sftp = endpoint("host", "127.0.0.1", "basePath", "/data",
            "hostKeySha256", "SHA256:test");
        Map<String, Object> doris = endpoint("host", "127.0.0.1", "database", "demo");
        Map<String, Object> clickhouse = endpoint("host", "127.0.0.1", "database", "default");

        catalog.validate("mysql", mysql);
        catalog.validate("oracle", oracle);
        catalog.validate("redis", redis);
        catalog.validate("sftp", sftp);
        catalog.validate("doris", doris);
        catalog.validate("clickhouse", clickhouse);

        assertThat(mysql).containsEntry("port", 3306).containsEntry("sslMode", "PREFERRED");
        assertThat(oracle).containsEntry("port", 1521);
        assertThat(redis).containsEntry("port", 6379).containsEntry("database", 0);
        assertThat(sftp).containsEntry("port", 22);
        assertThat(doris).containsEntry("port", 9030);
        assertThat(clickhouse).containsEntry("port", 8123).containsEntry("ssl", false);
    }

    @Test
    void rejectsUnregisteredConfigurationInsteadOfAcceptingArbitraryUrls() {
        Map<String, Object> endpoint = endpoint("host", "127.0.0.1", "database", "demo",
            "jdbcUrl", "jdbc:mysql://untrusted");

        assertThatThrownBy(() -> catalog.validate("mysql", endpoint))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未注册配置项");
    }

    private static List<ConnectorDescriptorProvider> providers() {
        List<SecretProvider> secrets = List.of();
        return List.of(new ClickHouseConnectionTester(secrets), new DorisConnectionTester(secrets),
            new MySqlConnectionTester(secrets), new OracleConnectionTester(secrets),
            new RedisConnectionTester(secrets), new SftpConnectionTester(secrets));
    }

    private static Map<String, Object> endpoint(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index].toString(), values[index + 1]);
        }
        return result;
    }
}

package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.connector.ConnectorFieldDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorPreviewDescriptor;
import com.xfwang.govdata.datasource.infrastructure.connector.clickhouse.ClickHouseConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.http.HttpApiConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.http.HttpApiEndpointValidator;
import com.xfwang.govdata.datasource.infrastructure.connector.http.HttpTargetPolicy;
import com.xfwang.govdata.datasource.infrastructure.connector.mysql.MySqlConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.oracle.OracleConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.postgresql.PostgreSqlConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.redis.RedisConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.sftp.SftpConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.sftp.SftpEndpointValidator;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
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

    private final HttpTargetPolicy httpTargetPolicy = new HttpTargetPolicy();
    private final ConnectorCatalog catalog = new ConnectorCatalog(providers(httpTargetPolicy),
        List.of(new HttpApiEndpointValidator(httpTargetPolicy), new SftpEndpointValidator()));

    @Test
    void exposesFirstVersionConnectors() {
        assertThat(catalog.list()).extracting(ConnectorDescriptor::getConnectorCode)
            .containsExactly("clickhouse", "http-api", "mysql", "oracle", "postgresql", "redis", "sftp");
        assertThat(catalog.list()).allSatisfy(descriptor -> {
            assertThat(descriptor.getConnectorVersion()).isEqualTo("1.0");
            assertThat(descriptor.getCapabilities()).contains("CONNECTION_TEST");
            assertThat(descriptor.getAuthProfiles()).isNotEmpty().allSatisfy(profile -> {
                assertThat(profile.getProfileCode()).isNotBlank();
                if (!"NONE".equals(profile.getProfileCode())) {
                    assertThat(profile.getSecretType()).isNotNull();
                }
            });
        });
    }

    @Test
    void generatesConnectionTargetFromValidatedBasicFields() {
        Map<String, Object> endpoint = new HashMap<>();
        endpoint.put("host", "127.0.0.1");
        endpoint.put("port", 3306);
        endpoint.put("database", "demo");
        endpoint.put("properties", Map.of("connectTimeout", "5000"));

        catalog.validate("mysql", "PASSWORD", "DEV", endpoint);

        assertThat(endpoint).containsEntry("connectionTarget", "jdbc:mysql://127.0.0.1:3306/demo")
            .doesNotContainKey("jdbcUrl");
    }

    @Test
    void supportsNonJdbcConnectionPreviewWithoutJdbcSpecificContract() {
        ConnectorDescriptorProvider sftp = () -> ConnectorDescriptor.builder()
            .connectorCode("sftp").connectorName("SFTP").connectorVersion("1.0")
            .category("FILE_TRANSFER")
            .connectionPreview(ConnectorPreviewDescriptor.builder()
                .label("连接地址").template("sftp://{host}:{port}/{rootPath}").build())
            .endpointFields(List.of(
                ConnectorFieldDescriptor.builder().code("host").label("主机地址")
                    .valueType("STRING").required(true).build(),
                ConnectorFieldDescriptor.builder().code("port").label("端口")
                    .valueType("INTEGER").required(true).defaultValue(22).build(),
                ConnectorFieldDescriptor.builder().code("rootPath").label("根目录")
                    .valueType("STRING").required(true).build()))
            .authProfiles(List.of(ConnectorAuthProfile.builder().profileCode("PASSWORD")
                .profileName("用户名和密码").secretType(SecretType.USERNAME_PASSWORD).build()))
            .build();
        ConnectorCatalog nonJdbcCatalog = new ConnectorCatalog(List.of(sftp), List.of());
        Map<String, Object> endpoint = new HashMap<>(Map.of(
            "host", "sftp.local", "port", 22, "rootPath", "exchange"));

        nonJdbcCatalog.validate("sftp", "PASSWORD", "DEV", endpoint);

        assertThat(endpoint).containsEntry("connectionTarget", "sftp://sftp.local:22/exchange");
    }

    @Test
    void acceptsRedisZeroDatabaseUsingConnectorNumericConstraints() {
        Map<String, Object> endpoint = new HashMap<>(Map.of(
            "protocol", "redis", "host", "127.0.0.1", "port", 6379, "database", 0));

        catalog.validate("redis", "ACL_PASSWORD", "DEV", endpoint);

        assertThat(endpoint).containsEntry("connectionTarget", "redis://127.0.0.1:6379/0");

        Map<String, Object> unsupportedProperties = new HashMap<>(Map.of(
            "protocol", "redis", "host", "127.0.0.1", "port", 6379, "database", 0,
            "properties", Map.of("socketTimeout", "5000")));
        assertThatThrownBy(() -> catalog.validate("redis", "ACL_PASSWORD", "DEV", unsupportedProperties))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不支持自定义连接属性");
    }

    @Test
    void rejectsClientSubmittedConnectionTargetsAndSensitiveProperties() {
        assertThatThrownBy(() -> catalog.validate("mysql", "PASSWORD", "DEV",
            new HashMap<>(Map.of("connectionTarget", "jdbc:mysql://db/demo"))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不可提交");
        assertThatThrownBy(() -> catalog.validate("mysql", "PASSWORD", "DEV",
            new HashMap<>(Map.of("jdbcUrl", "jdbc:mysql://db/demo?user=root&password=test"))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不可提交");
        assertThatThrownBy(() -> catalog.validate("mysql", "PASSWORD", "DEV", new HashMap<>(Map.of(
            "host", "db.local", "port", 3306, "database", "demo",
            "properties", Map.of("password", "test")))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("敏感字段");
    }

    @Test
    void validatesHttpConditionalAuthenticationFields() {
        Map<String, Object> none = new HashMap<>(Map.of(
            "baseUrl", "http://api.example.test", "healthCheckPath", "/health",
            "healthCheckMethod", "GET", "connectTimeoutSeconds", 5, "readTimeoutSeconds", 10));
        catalog.validate("http-api", "NONE", "DEV", none);
        assertThat(none).containsEntry("connectionTarget", "http://api.example.test");

        Map<String, Object> oauth2 = new HashMap<>(none);
        oauth2.remove("connectionTarget");
        oauth2.put("baseUrl", "https://api.example.test");
        oauth2.put("tokenEndpoint", "https://auth.example.test/oauth/token");
        catalog.validate("http-api", "OAUTH2_CLIENT_CREDENTIALS", "PROD", oauth2);

        assertThatThrownBy(() -> catalog.validate("http-api", "NONE", "DEV",
            new HashMap<>(Map.of("baseUrl", "file:///etc/passwd", "healthCheckPath", "/",
                "healthCheckMethod", "GET", "connectTimeoutSeconds", 5, "readTimeoutSeconds", 10))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("Base URL");
    }

    @Test
    void validatesSftpAbsoluteRootAndFingerprint() {
        Map<String, Object> endpoint = new HashMap<>(Map.of(
            "host", "sftp.example.test", "port", 22, "rootPath", "/exchange",
            "charset", "UTF-8", "connectTimeoutSeconds", 10,
            "hostKeyFingerprint", "SHA256:abcdefghijklmnopqrstuvxyz0123456789ABCDEFG"));

        catalog.validate("sftp", "PASSWORD", "PROD", endpoint);

        assertThat(endpoint).containsEntry("connectionTarget", "sftp://sftp.example.test:22/exchange");
    }

    private static List<ConnectorDescriptorProvider> providers(HttpTargetPolicy policy) {
        List<SecretProvider> secrets = List.of();
        return List.of(new ClickHouseConnectionTester(secrets), new MySqlConnectionTester(secrets),
            new HttpApiConnectionTester(secrets, new com.xfwang.govdata.datasource.infrastructure.connector.http.AccessTokenProvider() {
                @Override public com.xfwang.govdata.datasource.infrastructure.connector.http.OAuth2AccessToken acquire(
                    com.xfwang.govdata.datasource.infrastructure.connector.http.OAuth2TokenRequest request) { return null; }
                @Override public com.xfwang.govdata.datasource.infrastructure.connector.http.OAuth2AccessToken refresh(
                    com.xfwang.govdata.datasource.infrastructure.connector.http.OAuth2TokenRequest request) { return null; }
            }, policy),
            new OracleConnectionTester(secrets), new PostgreSqlConnectionTester(secrets),
            new RedisConnectionTester(secrets), new SftpConnectionTester(secrets));
    }
}

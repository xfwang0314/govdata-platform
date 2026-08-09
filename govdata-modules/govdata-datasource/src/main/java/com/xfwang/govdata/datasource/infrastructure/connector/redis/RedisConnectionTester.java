package com.xfwang.govdata.datasource.infrastructure.connector.redis;

import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorPreviewDescriptor;
import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.AbstractConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorProvider;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureClassifier;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.choiceField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.integerField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.textField;

/**
 * Redis 单机 ACL / Password 连通测试 Connector。
 *
 * <p>继承自 {@link AbstractConnectionTester}，使用 Lettuce 客户端执行连接测试与 PING 命令校验。</p>
 */
@Component
public class RedisConnectionTester extends AbstractConnectionTester implements ConnectorDescriptorProvider {

    /**
     * 构造函数，注入凭据解析服务。
     *
     * @param secretProviders 凭据解析提供者列表
     */
    public RedisConnectionTester(List<SecretProvider> secretProviders) {
        super(secretProviders);
    }

    @Override
    protected String connectorCode() {
        return "redis";
    }

    @Override
    protected String connectorVersion() {
        return "1.0";
    }

    /**
     * 执行 Redis 连接测试。
     *
     * @param request 连通测试请求
     * @return 连通测试结果
     */
    @Override
    public ConnectionTestResult test(ConnectionTestRequest request) {
        String protocol = text(request, "protocol");
        String host = text(request, "host");
        int port = integer(request, "port");
        int database = integer(request, "database");
        if ((!"redis".equals(protocol) && !"rediss".equals(protocol)) || host.isBlank()
            || port < 1 || port > 65535 || database < 0 || database > 15) {
            return failed(ConnectionTestReason.ENDPOINT_INVALID, null, 0);
        }
        boolean aclAuthentication = "ACL_PASSWORD".equals(request.getAuthProfileCode());
        boolean passwordAuthentication = "PASSWORD".equals(request.getAuthProfileCode());
        if (!aclAuthentication && !passwordAuthentication) {
            return failed(ConnectionTestReason.ENDPOINT_INVALID, null, 0);
        }
        SecretType secretType = aclAuthentication ? SecretType.USERNAME_PASSWORD : SecretType.PASSWORD;
        Set<String> requiredKeys = secretType.requiredKeys();
        Optional<SecretValue> resolved = resolveSecret(request, secretType, requiredKeys);
        if (resolved.isEmpty()) {
            return failed(ConnectionTestReason.SECRET_UNAVAILABLE, null, 0);
        }
        long started = System.nanoTime();
        try (SecretValue secret = resolved.orElseThrow()) {
            char[] username = secret.get("username");
            char[] password = secret.get("password");
            if ((aclAuthentication && username == null) || password == null) {
                return failed(ConnectionTestReason.SECRET_FIELDS_MISSING, null, elapsed(started));
            }
            try {
                return ping(protocol, host, port, database, aclAuthentication ? username : null, password, started);
            } finally {
                if (username != null) {
                    Arrays.fill(username, '\0');
                }
                Arrays.fill(password, '\0');
            }
        } catch (Exception exception) {
            return failed(ConnectionFailureClassifier.classify(exception), null, elapsed(started));
        }
    }

    /**
     * 发送 PING 指令验证 Redis 节点响应。
     */
    private ConnectionTestResult ping(String protocol, String host, int port, int database,
                                       char[] username, char[] password, long started) {
        RedisURI.Builder uriBuilder = RedisURI.Builder.redis(host, port)
            .withDatabase(database)
            .withSsl("rediss".equals(protocol))
            .withTimeout(Duration.ofSeconds(5));
        if (username == null) {
            uriBuilder.withPassword(password);
        } else {
            uriBuilder.withAuthentication(new String(username), password);
        }
        RedisURI uri = uriBuilder.build();
        RedisClient client = RedisClient.create(uri);
        client.setDefaultTimeout(Duration.ofSeconds(5));
        try (client; StatefulRedisConnection<String, String> connection = client.connect()) {
            if (!"PONG".equalsIgnoreCase(connection.sync().ping())) {
                return failed(ConnectionTestReason.PING_FAILED, null, elapsed(started));
            }
            return ConnectionTestResult.builder()
                .success(true)
                .reasonCode(ConnectionTestReason.CONNECTED.code())
                .message(ConnectionTestReason.CONNECTED.message())
                .latencyMs(elapsed(started))
                .serverInfo(Map.of("protocol", protocol.toUpperCase(), "database", database))
                .build();
        }
    }

    /**
     * 提供 Redis Connector 元数据描述符。
     *
     * @return Connector 描述符
     */
    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder()
            .connectorCode("redis")
            .connectorName("Redis")
            .connectorVersion("1.0")
            .category("KEY_VALUE")
            .capabilities(Set.of("CONNECTION_TEST"))
            .connectionPreview(ConnectorPreviewDescriptor.builder()
                .label("Redis 地址")
                .template("{protocol}://{host}:{port}/{database}")
                .build())
            .endpointFields(List.of(
                choiceField("protocol", "协议", "redis", List.of("redis", "rediss")),
                textField("host", "主机地址", true, "Redis 单机服务器域名或 IP"),
                integerField("port", "端口", true, 6379),
                integerField("database", "逻辑库", true, 0, 0, 15)))
            .authProfiles(List.of(
                ConnectorAuthProfile.builder()
                    .profileCode("ACL_PASSWORD")
                    .profileName("ACL 用户名和密码")
                    .secretType(SecretType.USERNAME_PASSWORD)
                    .build(),
                ConnectorAuthProfile.builder()
                    .profileCode("PASSWORD")
                    .profileName("仅密码")
                    .secretType(SecretType.PASSWORD)
                    .build()))
            .build();
    }
}

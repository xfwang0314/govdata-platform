package com.xfwang.govdata.datasource.connector.redis;

import com.xfwang.govdata.datasource.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.connector.ConnectionTestResult;
import com.xfwang.govdata.datasource.connector.ConnectionTester;
import com.xfwang.govdata.datasource.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.connector.ConnectorDescriptorProvider;
import com.xfwang.platform.secret.api.SecretProvider;
import com.xfwang.platform.secret.api.SecretValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.xfwang.govdata.datasource.connector.ConnectorDescriptorSupport.*;

/** Redis 单机连接测试 Connector。 */
@Component
public class RedisConnectionTester implements ConnectionTester, ConnectorDescriptorProvider {

    private static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9._-]{1,253}$");
    private final List<SecretProvider> secretProviders;

    public RedisConnectionTester(List<SecretProvider> secretProviders) {
        this.secretProviders = secretProviders;
    }

    @Override public boolean supports(String code, String version) {
        return "redis".equalsIgnoreCase(code) && "1.0".equals(version);
    }

    @Override public ConnectionTestResult test(ConnectionTestRequest request) {
        String host = value(request, "host");
        int port = integer(request, "port");
        int database = integer(request, "database");
        if (!HOST.matcher(host).matches() || port < 1 || port > 65535 || database < 0 || database > 15) {
            return failed("ENDPOINT_INVALID", "host、port 或 database 配置无效", 0);
        }
        Optional<SecretValue> resolved = secretProviders.stream()
            .map(provider -> provider.resolve(request.getSecretRef(), "DATASOURCE_CONNECTION_TEST"))
            .flatMap(Optional::stream).findFirst();
        if (resolved.isEmpty()) {
            return failed("SECRET_UNAVAILABLE", "SecretRef 无法解析", 0);
        }
        long started = System.nanoTime();
        try (SecretValue secret = resolved.orElseThrow()) {
            char[] password = secret.get("password");
            char[] username = secret.get("username");
            RedisURI.Builder builder = RedisURI.Builder.redis(host, port).withDatabase(database)
                .withTimeout(Duration.ofSeconds(5));
            if (password != null && password.length > 0) {
                if (username != null && username.length > 0) {
                    builder.withAuthentication(new String(username), password);
                } else {
                    builder.withPassword(password);
                }
            }
            builder.withSsl(Boolean.parseBoolean(value(request, "ssl")));
            RedisClient client = RedisClient.create(builder.build());
            client.setDefaultTimeout(Duration.ofSeconds(5));
            try (client; StatefulRedisConnection<String, String> connection = client.connect()) {
                String pong = connection.sync().ping();
                boolean success = "PONG".equalsIgnoreCase(pong);
                return ConnectionTestResult.builder().success(success)
                    .reasonCode(success ? "CONNECTED" : "PING_FAILED")
                    .message(success ? "连接成功" : "Redis PING 校验失败")
                    .latencyMs(elapsed(started)).serverInfo(Map.of("protocol", "RESP", "database", database)).build();
            }
        } catch (Exception ignored) {
            return failed("CONNECTION_FAILED", "连接失败，请检查端点、凭据和网络", elapsed(started));
        }
    }

    @Override public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder().connectorCode("redis").connectorName("Redis")
            .connectorVersion("1.0").category("KEY_VALUE").capabilities(Set.of("CONNECTION_TEST"))
            .endpointFields(List.of(textField("host", "主机", true, "Redis 单机地址"),
                integerField("port", "端口", true, 6379), integerField("database", "逻辑库", true, 0),
                booleanField("ssl", "启用 TLS", false, "生产环境建议启用")))
            .secretKeys(List.of("username", "password")).build();
    }

    private String value(ConnectionTestRequest request, String key) {
        Object value = request.getEndpointConfig().get(key);
        return value == null ? "" : value.toString().trim();
    }
    private int integer(ConnectionTestRequest request, String key) {
        try { return Integer.parseInt(value(request, key)); } catch (NumberFormatException ignored) { return -1; }
    }
    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private ConnectionTestResult failed(String code, String message, long latency) {
        return ConnectionTestResult.builder().success(false).reasonCode(code).message(message).latencyMs(latency).build();
    }
}

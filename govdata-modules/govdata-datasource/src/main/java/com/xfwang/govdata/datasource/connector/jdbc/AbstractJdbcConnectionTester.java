package com.xfwang.govdata.datasource.connector.jdbc;

import com.xfwang.govdata.datasource.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.connector.ConnectionTestResult;
import com.xfwang.govdata.datasource.connector.ConnectionTester;
import com.xfwang.platform.secret.api.SecretProvider;
import com.xfwang.platform.secret.api.SecretValue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/** JDBC Connector 共用的凭据解析、超时、资源释放和异常脱敏实现。 */
public abstract class AbstractJdbcConnectionTester implements ConnectionTester {

    protected static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9._-]{1,253}$");
    protected static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_$-]{1,128}$");
    private final List<SecretProvider> secretProviders;

    protected AbstractJdbcConnectionTester(List<SecretProvider> secretProviders) {
        this.secretProviders = secretProviders;
    }

    @Override
    public boolean supports(String connectorCode, String connectorVersion) {
        return code().equalsIgnoreCase(connectorCode) && version().equals(connectorVersion);
    }

    @Override
    public final ConnectionTestResult test(ConnectionTestRequest request) {
        String validationMessage = validateEndpoint(request);
        if (validationMessage != null) {
            return failed("ENDPOINT_INVALID", validationMessage, 0);
        }
        Optional<SecretValue> resolved = secretProviders.stream()
            .map(provider -> provider.resolve(request.getSecretRef(), "DATASOURCE_CONNECTION_TEST"))
            .flatMap(Optional::stream).findFirst();
        if (resolved.isEmpty()) {
            return failed("SECRET_UNAVAILABLE", "SecretRef 无法解析", 0);
        }
        long started = System.nanoTime();
        try (SecretValue secret = resolved.orElseThrow()) {
            char[] username = secret.get("username");
            char[] password = secret.get("password");
            if (username == null || password == null) {
                return failed("SECRET_FIELDS_MISSING", "凭据缺少 username 或 password", elapsed(started));
            }
            Class.forName(driverClassName());
            Properties properties = new Properties();
            properties.setProperty("user", new String(username));
            properties.setProperty("username", new String(username));
            properties.setProperty("password", new String(password));
            configure(properties, request);
            try (Connection connection = DriverManager.getConnection(jdbcUrl(request), properties)) {
                if (!connection.isValid(5)) {
                    return failed("CONNECTION_INVALID", "连接建立后校验失败", elapsed(started));
                }
                return ConnectionTestResult.builder()
                    .success(true).reasonCode("CONNECTED").message("连接成功")
                    .latencyMs(elapsed(started))
                    .serverInfo(Map.of(
                        "product", connection.getMetaData().getDatabaseProductName(),
                        "version", connection.getMetaData().getDatabaseProductVersion(),
                        "driver", connection.getMetaData().getDriverName()))
                    .build();
            }
        } catch (Exception ignored) {
            return failed("CONNECTION_FAILED", "连接失败，请检查端点、凭据、驱动和网络", elapsed(started));
        }
    }

    protected abstract String code();
    protected abstract String version();
    protected abstract String driverClassName();
    protected abstract String jdbcUrl(ConnectionTestRequest request);

    protected String validateEndpoint(ConnectionTestRequest request) {
        String host = text(request, "host");
        int port = integer(request, "port");
        return !HOST.matcher(host).matches() || port < 1 || port > 65535
            ? "host 或 port 配置无效" : null;
    }

    protected void configure(Properties properties, ConnectionTestRequest request) {
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "5000");
    }

    protected String text(ConnectionTestRequest request, String key) {
        Object value = request.getEndpointConfig().get(key);
        return value == null ? "" : value.toString().trim();
    }

    protected int integer(ConnectionTestRequest request, String key) {
        try {
            return Integer.parseInt(text(request, key));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    protected ConnectionTestResult failed(String reasonCode, String message, long latencyMs) {
        return ConnectionTestResult.builder().success(false).reasonCode(reasonCode)
            .message(message).latencyMs(latencyMs).build();
    }

    protected long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}

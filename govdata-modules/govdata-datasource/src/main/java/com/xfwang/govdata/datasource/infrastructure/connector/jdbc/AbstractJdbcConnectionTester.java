package com.xfwang.govdata.datasource.infrastructure.connector.jdbc;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.AbstractConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureClassifier;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * JDBC 类型 Connector 的抽象测试基类。
 *
 * <p>继承自 {@link AbstractConnectionTester}，统一实现了 JDBC Driver 类的加载、参数配置、
 * 凭据解析、DriverManager 连接建立以及连接有效性验证等标准模版流程。</p>
 */
public abstract class AbstractJdbcConnectionTester extends AbstractConnectionTester {

    /**
     * 构造函数，注入凭据解析提供者。
     *
     * @param secretProviders 凭据解析提供者列表
     */
    protected AbstractJdbcConnectionTester(List<SecretProvider> secretProviders) {
        super(secretProviders);
    }

    /**
     * 执行 JDBC 连接测试的统一模版流程。
     *
     * @param request 连通测试请求
     * @return 连通测试结果
     */
    @Override
    public final ConnectionTestResult test(ConnectionTestRequest request) {
        String validationMessage = validateEndpoint(request);
        if (validationMessage != null) {
            return failed(ConnectionTestReason.ENDPOINT_INVALID, validationMessage, 0);
        }
        Optional<SecretValue> resolved = resolveSecret(request, SecretType.USERNAME_PASSWORD, SecretType.USERNAME_PASSWORD.requiredKeys());
        if (resolved.isEmpty()) {
            return failed(ConnectionTestReason.SECRET_UNAVAILABLE, null, 0);
        }
        long started = System.nanoTime();
        try (SecretValue secret = resolved.orElseThrow()) {
            char[] username = secret.get("username");
            char[] password = secret.get("password");
            if (username == null || password == null) {
                return failed(ConnectionTestReason.SECRET_FIELDS_MISSING, null, elapsed(started));
            }
            Class.forName(driverClassName());
            Properties properties = new Properties();
            properties.setProperty("user", new String(username));
            properties.setProperty("username", new String(username));
            properties.setProperty("password", new String(password));
            driverProperties(request).forEach(properties::setProperty);
            configure(properties, request);
            try (Connection connection = DriverManager.getConnection(connectionTarget(request), properties)) {
                if (!validateConnection(connection)) {
                    return failed(ConnectionTestReason.CONNECTION_INVALID, null, elapsed(started));
                }
                return ConnectionTestResult.builder()
                    .success(true)
                    .reasonCode(ConnectionTestReason.CONNECTED.code())
                    .message(ConnectionTestReason.CONNECTED.message())
                    .latencyMs(elapsed(started))
                    .serverInfo(Map.of(
                        "product", connection.getMetaData().getDatabaseProductName(),
                        "version", connection.getMetaData().getDatabaseProductVersion(),
                        "driver", connection.getMetaData().getDriverName()))
                    .build();
            }
        } catch (Exception exception) {
            ConnectionTestReason reason = ConnectionFailureClassifier.classify(exception);
            return failed(reason, null, elapsed(started));
        }
    }

    /**
     * 兼容方法：返回 Connector 编码。
     *
     * @return Connector 编码
     */
    protected abstract String code();

    /**
     * 兼容方法：返回 Connector 版本。
     *
     * @return Connector 版本
     */
    protected abstract String version();

    @Override
    protected String connectorCode() {
        return code();
    }

    @Override
    protected String connectorVersion() {
        return version();
    }

    /**
     * 获取数据库驱动类全类名。
     *
     * @return 驱动类全名
     */
    protected abstract String driverClassName();

    /**
     * 验证端点配置参数是否完备。
     *
     * @param request 连通测试请求
     * @return 验证失败时的错误消息，成功时返回 null
     */
    protected String validateEndpoint(ConnectionTestRequest request) {
        return connectionTarget(request).isBlank() ? "JDBC 连接目标不能为空" : null;
    }

    /**
     * 计算 JDBC 连接目标 URL。
     *
     * @param request 连通测试请求
     * @return 连接目标字符串
     */
    private String connectionTarget(ConnectionTestRequest request) {
        String target = text(request, "connectionTarget");
        return target.isBlank() ? text(request, "jdbcUrl") : target;
    }

    /**
     * 扩展配置钩子：子类可覆盖此方法设置特定 JDBC 超时或额外参数。
     *
     * @param properties 配置属性
     * @param request    连通测试请求
     */
    protected void configure(Properties properties, ConnectionTestRequest request) {
        // 超时参数名称和单位由具体 JDBC Adapter 声明。
    }

    /**
     * 验证 JDBC Connection 是否存活有效。
     *
     * @param connection 已建立的 JDBC 连接
     * @return 有效返回 true，否则返回 false
     * @throws java.sql.SQLException 如果数据库校验失败
     */
    protected boolean validateConnection(Connection connection) throws java.sql.SQLException {
        return connection.isValid(5);
    }

    /**
     * 提取驱动特定附加属性。
     *
     * @param request 连通测试请求
     * @return 属性 Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, String> driverProperties(ConnectionTestRequest request) {
        Object value = request.getEndpointConfig().get("properties");
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }
}

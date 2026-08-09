package com.xfwang.govdata.datasource.infrastructure.connector.clickhouse;

import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorPreviewDescriptor;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorProvider;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.AbstractJdbcConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.JdbcConnectorProfile;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.choiceField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.integerField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.textField;

/**
 * ClickHouse HTTP(S) JDBC 连接测试 Connector。
 *
 * <p>继承自 {@link AbstractJdbcConnectionTester}，提供 ClickHouse v2 驱动参数适配与 `SELECT 1` 心跳查询验证。</p>
 */
@Component
public class ClickHouseConnectionTester extends AbstractJdbcConnectionTester implements ConnectorDescriptorProvider {

    private static final JdbcConnectorProfile PROFILE = JdbcConnectorProfile.CLICKHOUSE;

    /**
     * 构造函数，注入凭据解析提供者。
     *
     * @param providers 凭据解析提供者列表
     */
    public ClickHouseConnectionTester(List<SecretProvider> providers) {
        super(providers);
    }

    @Override
    protected String code() {
        return PROFILE.code();
    }

    @Override
    protected String version() {
        return PROFILE.version();
    }

    @Override
    protected String driverClassName() {
        return PROFILE.driverClassName();
    }

    @Override
    protected void configure(Properties properties, ConnectionTestRequest request) {
        // JDBC v2 将 username 视为未映射配置，只接受 JDBC 标准的 user 属性。
        properties.remove("username");
        properties.putIfAbsent("connection_timeout", "5000");
        properties.putIfAbsent("socket_timeout", "5000");
    }

    @Override
    protected boolean validateConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next() && resultSet.getInt(1) == 1;
        }
    }

    /**
     * 提供 ClickHouse Connector 元数据描述符。
     *
     * @return Connector 描述符
     */
    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder()
            .connectorCode(code())
            .connectorName("ClickHouse")
            .connectorVersion(version())
            .category("JDBC")
            .capabilities(Set.of("CONNECTION_TEST", "METADATA_INSPECT"))
            .customPropertiesSupported(true)
            .connectionPreview(ConnectorPreviewDescriptor.builder()
                .label("JDBC URL")
                .template("jdbc:ch:{protocol}://{host}:{port}/{database}")
                .build())
            .endpointFields(List.of(
                choiceField("protocol", "协议", "http", List.of("http", "https")),
                textField("host", "主机地址", true, "ClickHouse 服务器域名或 IP"),
                integerField("port", "端口", true, 8123),
                textField("database", "数据库名", true, "要连接的数据库名称")))
            .authProfiles(List.of(passwordProfile()))
            .build();
    }

    private ConnectorAuthProfile passwordProfile() {
        return ConnectorAuthProfile.builder()
            .profileCode("PASSWORD")
            .profileName("用户名和密码")
            .secretType(SecretType.USERNAME_PASSWORD)
            .build();
    }
}

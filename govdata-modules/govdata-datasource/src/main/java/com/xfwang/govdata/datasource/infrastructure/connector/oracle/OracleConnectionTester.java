package com.xfwang.govdata.datasource.infrastructure.connector.oracle;

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

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.integerField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.textField;

/**
 * Oracle Thin JDBC 连通测试 Connector。
 *
 * <p>继承自 {@link AbstractJdbcConnectionTester}，支持基于 Oracle Service Name 的 Thin 驱动连接测试。</p>
 */
@Component
public class OracleConnectionTester extends AbstractJdbcConnectionTester implements ConnectorDescriptorProvider {

    private static final JdbcConnectorProfile PROFILE = JdbcConnectorProfile.ORACLE;

    /**
     * 构造函数，注入凭据解析提供者。
     *
     * @param secretProviders 凭据解析提供者列表
     */
    public OracleConnectionTester(List<SecretProvider> secretProviders) {
        super(secretProviders);
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
        properties.putIfAbsent("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.putIfAbsent("oracle.jdbc.ReadTimeout", "5000");
    }

    /**
     * 提供 Oracle Connector 元数据描述符。
     *
     * @return Connector 描述符
     */
    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder()
            .connectorCode(code())
            .connectorName("Oracle")
            .connectorVersion(version())
            .category("JDBC")
            .capabilities(Set.of("CONNECTION_TEST", "METADATA_INSPECT"))
            .customPropertiesSupported(true)
            .connectionPreview(ConnectorPreviewDescriptor.builder()
                .label("JDBC URL")
                .template("jdbc:oracle:thin:@//{host}:{port}/{serviceName}")
                .build())
            .endpointFields(List.of(
                textField("host", "主机地址", true, "Oracle 服务器域名或 IP"),
                integerField("port", "端口", true, 1521),
                textField("serviceName", "服务名", true, "Oracle Service Name")))
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

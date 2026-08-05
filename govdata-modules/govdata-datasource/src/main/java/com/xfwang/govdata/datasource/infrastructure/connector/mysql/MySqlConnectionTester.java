package com.xfwang.govdata.datasource.infrastructure.connector.mysql;

import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorProvider;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.AbstractJdbcConnectionTester;
import com.xfwang.platform.api.secret.SecretProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.*;

/** MySQL 8 Connector。 */
@Component
public class MySqlConnectionTester extends AbstractJdbcConnectionTester implements ConnectorDescriptorProvider {

    public MySqlConnectionTester(List<SecretProvider> secretProviders) {
        super(secretProviders);
    }

    @Override protected String code() { return "mysql"; }
    @Override protected String version() { return "1.0"; }
    @Override protected String driverClassName() { return "com.mysql.cj.jdbc.Driver"; }

    @Override
    protected String jdbcUrl(ConnectionTestRequest request) {
        return "jdbc:mysql://" + text(request, "host") + ':' + integer(request, "port") + '/'
            + text(request, "database");
    }

    @Override
    protected String validateEndpoint(ConnectionTestRequest request) {
        String invalid = super.validateEndpoint(request);
        if (invalid != null || !IDENTIFIER.matcher(text(request, "database")).matches()) {
            return "host、port 或 database 配置无效";
        }
        boolean publicKey = Boolean.parseBoolean(text(request, "allowPublicKeyRetrieval"));
        return publicKey && "PROD".equalsIgnoreCase(request.getEnvironment())
            ? "生产环境禁止 allowPublicKeyRetrieval" : null;
    }

    @Override
    protected void configure(Properties properties, ConnectionTestRequest request) {
        super.configure(properties, request);
        properties.setProperty("sslMode", text(request, "sslMode"));
        properties.setProperty("allowPublicKeyRetrieval", text(request, "allowPublicKeyRetrieval"));
    }

    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder().connectorCode(code()).connectorName("MySQL")
            .connectorVersion(version()).category("JDBC").secretType("USERNAME_PASSWORD").capabilities(Set.of("CONNECTION_TEST"))
            .endpointFields(List.of(
                textField("host", "主机", true, "MySQL 地址"), integerField("port", "端口", true, 3306),
                textField("database", "数据库", true, "数据库名称"),
                choiceField("sslMode", "SSL 模式", "PREFERRED",
                    List.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")),
                booleanField("allowPublicKeyRetrieval", "允许获取公钥", false, "仅开发环境按需开启")))
            .secretKeys(List.of("username", "password")).build();
    }
}

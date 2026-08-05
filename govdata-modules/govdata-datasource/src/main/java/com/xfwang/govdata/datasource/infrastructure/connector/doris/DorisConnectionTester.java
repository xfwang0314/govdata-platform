package com.xfwang.govdata.datasource.infrastructure.connector.doris;

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

/** 通过 Doris MySQL 协议端口测试连接。 */
@Component
public class DorisConnectionTester extends AbstractJdbcConnectionTester implements ConnectorDescriptorProvider {
    public DorisConnectionTester(List<SecretProvider> providers) { super(providers); }
    @Override protected String code() { return "doris"; }
    @Override protected String version() { return "1.0"; }
    @Override protected String driverClassName() { return "com.mysql.cj.jdbc.Driver"; }
    @Override protected String jdbcUrl(ConnectionTestRequest request) {
        return "jdbc:mysql://" + text(request, "host") + ':' + integer(request, "port") + '/'
            + text(request, "database");
    }
    @Override protected String validateEndpoint(ConnectionTestRequest request) {
        String invalid = super.validateEndpoint(request);
        return invalid != null || !IDENTIFIER.matcher(text(request, "database")).matches()
            ? "host、port 或 database 配置无效" : null;
    }
    @Override protected void configure(Properties properties, ConnectionTestRequest request) {
        super.configure(properties, request);
        properties.setProperty("sslMode", text(request, "sslMode"));
    }
    @Override public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder().connectorCode(code()).connectorName("Apache Doris")
            .connectorVersion(version()).category("JDBC").secretType("USERNAME_PASSWORD").capabilities(Set.of("CONNECTION_TEST"))
            .endpointFields(List.of(textField("host", "FE 主机", true, "Doris FE 地址"),
                integerField("port", "MySQL 协议端口", true, 9030),
                textField("database", "数据库", true, "数据库名称"),
                choiceField("sslMode", "SSL 模式", "DISABLED", List.of("DISABLED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"))))
            .secretKeys(List.of("username", "password")).build();
    }
}

package com.xfwang.govdata.datasource.infrastructure.connector.clickhouse;

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

/** ClickHouse HTTP(S) JDBC Connector。 */
@Component
public class ClickHouseConnectionTester extends AbstractJdbcConnectionTester implements ConnectorDescriptorProvider {
    public ClickHouseConnectionTester(List<SecretProvider> providers) { super(providers); }
    @Override protected String code() { return "clickhouse"; }
    @Override protected String version() { return "1.0"; }
    @Override protected String driverClassName() { return "com.clickhouse.jdbc.ClickHouseDriver"; }
    @Override protected String jdbcUrl(ConnectionTestRequest request) {
        String scheme = Boolean.parseBoolean(text(request, "ssl")) ? "https" : "http";
        return "jdbc:ch:" + scheme + "://" + text(request, "host") + ':' + integer(request, "port")
            + '/' + text(request, "database");
    }
    @Override protected String validateEndpoint(ConnectionTestRequest request) {
        String invalid = super.validateEndpoint(request);
        return invalid != null || !IDENTIFIER.matcher(text(request, "database")).matches()
            ? "host、port 或 database 配置无效" : null;
    }
    @Override protected void configure(Properties properties, ConnectionTestRequest request) {
        properties.setProperty("connect_timeout", "5000");
        properties.setProperty("socket_timeout", "5000");
    }
    @Override public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder().connectorCode(code()).connectorName("ClickHouse")
            .connectorVersion(version()).category("JDBC").secretType("USERNAME_PASSWORD").capabilities(Set.of("CONNECTION_TEST"))
            .endpointFields(List.of(textField("host", "主机", true, "ClickHouse HTTP 地址"),
                integerField("port", "HTTP(S) 端口", true, 8123),
                textField("database", "数据库", true, "数据库名称"),
                booleanField("ssl", "启用 HTTPS", false, "ClickHouse Cloud 应启用")))
            .secretKeys(List.of("username", "password")).build();
    }
}

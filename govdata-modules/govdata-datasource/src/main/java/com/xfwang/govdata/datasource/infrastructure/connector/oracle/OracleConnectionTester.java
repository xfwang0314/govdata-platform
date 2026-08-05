package com.xfwang.govdata.datasource.infrastructure.connector.oracle;

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

/** Oracle Service Name 连接 Connector。 */
@Component
public class OracleConnectionTester extends AbstractJdbcConnectionTester implements ConnectorDescriptorProvider {

    public OracleConnectionTester(List<SecretProvider> secretProviders) { super(secretProviders); }
    @Override protected String code() { return "oracle"; }
    @Override protected String version() { return "1.0"; }
    @Override protected String driverClassName() { return "oracle.jdbc.OracleDriver"; }
    @Override protected String jdbcUrl(ConnectionTestRequest request) {
        return "jdbc:oracle:thin:@//" + text(request, "host") + ':' + integer(request, "port") + '/'
            + text(request, "serviceName");
    }
    @Override protected String validateEndpoint(ConnectionTestRequest request) {
        String invalid = super.validateEndpoint(request);
        return invalid != null || !IDENTIFIER.matcher(text(request, "serviceName")).matches()
            ? "host、port 或 serviceName 配置无效" : null;
    }
    @Override protected void configure(Properties properties, ConnectionTestRequest request) {
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "5000");
    }
    @Override public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder().connectorCode(code()).connectorName("Oracle")
            .connectorVersion(version()).category("JDBC").secretType("USERNAME_PASSWORD").capabilities(Set.of("CONNECTION_TEST"))
            .endpointFields(List.of(textField("host", "主机", true, "Oracle 地址"),
                integerField("port", "端口", true, 1521),
                textField("serviceName", "服务名", true, "第一版使用 Service Name，不使用 SID")))
            .secretKeys(List.of("username", "password")).build();
    }
}

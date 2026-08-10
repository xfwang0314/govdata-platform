package com.xfwang.govdata.datasource.infrastructure.connector.clickhouse;

import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.AbstractJdbcMetadataInspector;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.JdbcConnectorProfile;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * ClickHouse 元数据探测 Inspector。
 */
@Component
public class ClickHouseMetadataInspector extends AbstractJdbcMetadataInspector {

    public ClickHouseMetadataInspector(List<SecretProvider> providers) {
        super(providers);
    }

    @Override
    protected JdbcConnectorProfile profile() {
        return JdbcConnectorProfile.CLICKHOUSE;
    }

    @Override
    protected void configure(Properties properties, MetadataInspectRequest request) {
        properties.putIfAbsent("connection_timeout", "5000");
        properties.putIfAbsent("socket_timeout", "30000");
        properties.remove("username");
    }
}

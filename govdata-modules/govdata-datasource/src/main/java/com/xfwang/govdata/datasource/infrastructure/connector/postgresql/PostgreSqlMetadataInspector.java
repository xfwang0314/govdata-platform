package com.xfwang.govdata.datasource.infrastructure.connector.postgresql;

import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.AbstractJdbcMetadataInspector;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.JdbcConnectorProfile;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * PostgreSQL 元数据探测 Inspector。
 */
@Component
public class PostgreSqlMetadataInspector extends AbstractJdbcMetadataInspector {

    public PostgreSqlMetadataInspector(List<SecretProvider> providers) {
        super(providers);
    }

    @Override
    protected JdbcConnectorProfile profile() {
        return JdbcConnectorProfile.POSTGRESQL;
    }

    @Override
    protected void configure(Properties properties, MetadataInspectRequest request) {
        properties.putIfAbsent("connectTimeout", "5");
        properties.putIfAbsent("socketTimeout", "30");
    }
}

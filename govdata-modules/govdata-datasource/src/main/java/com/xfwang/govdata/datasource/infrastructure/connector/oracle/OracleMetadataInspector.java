package com.xfwang.govdata.datasource.infrastructure.connector.oracle;

import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.AbstractJdbcMetadataInspector;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.JdbcConnectorProfile;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * Oracle 元数据探测 Inspector。
 */
@Component
public class OracleMetadataInspector extends AbstractJdbcMetadataInspector {

    public OracleMetadataInspector(List<SecretProvider> providers) {
        super(providers);
    }

    @Override
    protected JdbcConnectorProfile profile() {
        return JdbcConnectorProfile.ORACLE;
    }

    @Override
    protected void configure(Properties properties, MetadataInspectRequest request) {
        properties.putIfAbsent("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.putIfAbsent("oracle.jdbc.ReadTimeout", "30000");
    }
}

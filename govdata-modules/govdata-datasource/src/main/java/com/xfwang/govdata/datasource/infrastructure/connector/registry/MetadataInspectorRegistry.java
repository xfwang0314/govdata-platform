package com.xfwang.govdata.datasource.infrastructure.connector.registry;

import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorMetadataInspector;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ConnectorMetadataInspector 策略注册表。
 */
@Component
@RequiredArgsConstructor
public class MetadataInspectorRegistry {

    private final List<ConnectorMetadataInspector> inspectors;

    public ConnectorMetadataInspector require(String connectorCode, String connectorVersion) {
        return inspectors.stream()
            .filter(inspector -> inspector.supports(connectorCode, connectorVersion))
            .findFirst()
            .orElseThrow(() -> new ServiceException(DataSourceErrorCode.CONNECTOR_UNAVAILABLE,
                "Connector 不支持元数据探测: " + connectorCode + "@" + connectorVersion));
    }
}

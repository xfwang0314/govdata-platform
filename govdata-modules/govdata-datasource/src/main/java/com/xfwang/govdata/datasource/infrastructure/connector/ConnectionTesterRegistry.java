package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;

/** 按声明能力选择 Connector，避免产品类型 switch。 */
@Component
@RequiredArgsConstructor
public class ConnectionTesterRegistry {

    private final List<ConnectionTester> testers;

    public ConnectionTester require(String connectorCode, String connectorVersion) {
        return testers.stream()
            .filter(tester -> tester.supports(connectorCode, connectorVersion))
            .findFirst()
            .orElseThrow(() -> new ServiceException(DataSourceErrorCode.CONNECTOR_UNAVAILABLE,
                "Connector 不支持连接测试: " + connectorCode + "@" + connectorVersion));
    }
}

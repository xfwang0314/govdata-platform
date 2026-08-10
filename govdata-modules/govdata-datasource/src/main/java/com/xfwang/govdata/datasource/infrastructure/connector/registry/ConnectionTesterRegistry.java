package com.xfwang.govdata.datasource.infrastructure.connector.registry;

import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTester;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ConnectionTester 策略注册表。
 */
@Component
@RequiredArgsConstructor
public class ConnectionTesterRegistry {

    private final List<ConnectionTester> testers;

    public ConnectionTester require(String connectorCode, String connectorVersion) {
        return testers.stream()
            .filter(tester -> tester.supports(connectorCode, connectorVersion))
            .findFirst()
            .orElseThrow(() -> new ServiceException(DataSourceErrorCode.CONNECTOR_UNAVAILABLE,
                "没有可用的 ConnectionTester: " + connectorCode + "@" + connectorVersion));
    }
}

package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;

/** Connector 的连通测试能力；其他能力使用独立接口扩展。 */
public interface ConnectionTester {
    boolean supports(String connectorCode, String connectorVersion);
    ConnectionTestResult test(ConnectionTestRequest request);
}

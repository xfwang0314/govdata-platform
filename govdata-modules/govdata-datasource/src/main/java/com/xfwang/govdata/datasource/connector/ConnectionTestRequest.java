package com.xfwang.govdata.datasource.connector;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/** 传给 Connector 的一次连接测试请求。 */
@Value
@Builder
public class ConnectionTestRequest {
    Long datasourceId;
    String connectorCode;
    String connectorVersion;
    String environment;
    Map<String, Object> endpointConfig;
    String secretRef;
}

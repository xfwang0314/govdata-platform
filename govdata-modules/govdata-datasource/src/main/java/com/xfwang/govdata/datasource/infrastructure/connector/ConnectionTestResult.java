package com.xfwang.govdata.datasource.infrastructure.connector;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/** Connector 返回的脱敏连接测试结果。 */
@Value
@Builder
public class ConnectionTestResult {
    boolean success;
    String reasonCode;
    String message;
    long latencyMs;
    @Builder.Default
    Map<String, Object> serverInfo = Map.of();
}

package com.xfwang.govdata.datasource.application.connectiontest.command;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class ConnectionTestRecordCommand {
    Long datasourceId;
    String connectorCode;
    String connectorVersion;
    String result;
    String reasonCode;
    String message;
    long latencyMs;
    Map<String, Object> serverInfo;
    String actorType;
    String actorId;
    String traceId;
    LocalDateTime occurredTime;
}

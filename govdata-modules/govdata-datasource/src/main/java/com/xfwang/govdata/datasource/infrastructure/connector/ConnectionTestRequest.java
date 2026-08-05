package com.xfwang.govdata.datasource.infrastructure.connector;

import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import com.xfwang.platform.api.secret.SecretResolveRequest;

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
    String tenantId;
    String actorType;
    String actorId;
    String purpose;
    String traceId;

    public SecretResolveRequest secretRequest(Set<String> requiredKeys) {
        return SecretResolveRequest.builder().tenantId(tenantId).secretRef(secretRef)
            .callerModule("govdata-datasource").actorType(actorType).actorId(actorId)
            .purpose(purpose).traceId(traceId).requiredKeys(requiredKeys).build();
    }
}

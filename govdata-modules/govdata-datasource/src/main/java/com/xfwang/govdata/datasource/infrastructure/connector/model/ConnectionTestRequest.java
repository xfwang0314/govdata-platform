package com.xfwang.govdata.datasource.infrastructure.connector.model;

import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretType;

import java.util.Map;
import java.util.Set;

/** 连通测试请求。 */
public class ConnectionTestRequest {
    private final Long datasourceId;
    private final String connectorCode;
    private final String connectorVersion;
    private final String authProfileCode;
    private final String environment;
    private final Map<String, Object> endpointConfig;
    private final String secretRef;
    private final String tenantId;
    private final String actorType;
    private final String actorId;
    private final String purpose;
    private final String traceId;

    public ConnectionTestRequest(Long datasourceId, String connectorCode, String connectorVersion,
                                 String authProfileCode, String environment, Map<String, Object> endpointConfig,
                                 String secretRef, String tenantId, String actorType, String actorId,
                                 String purpose, String traceId) {
        this.datasourceId = datasourceId;
        this.connectorCode = connectorCode;
        this.connectorVersion = connectorVersion;
        this.authProfileCode = authProfileCode;
        this.environment = environment;
        this.endpointConfig = endpointConfig;
        this.secretRef = secretRef;
        this.tenantId = tenantId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.purpose = purpose;
        this.traceId = traceId;
    }

    public Long getDatasourceId() { return datasourceId; }
    public String getConnectorCode() { return connectorCode; }
    public String getConnectorVersion() { return connectorVersion; }
    public String getAuthProfileCode() { return authProfileCode; }
    public String getEnvironment() { return environment; }
    public Map<String, Object> getEndpointConfig() { return endpointConfig; }
    public String getSecretRef() { return secretRef; }
    public String getTenantId() { return tenantId; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getPurpose() { return purpose; }
    public String getTraceId() { return traceId; }

    public SecretResolveRequest secretRequest(SecretType secretType, Set<String> requiredKeys) {
        return SecretResolveRequest.builder().tenantId(tenantId).secretRef(secretRef)
            .expectedSecretType(secretType)
            .callerModule(DataSourceSecretUsage.OWNER_MODULE).actorType(actorType).actorId(actorId)
            .purpose(purpose).traceId(traceId).requiredKeys(requiredKeys).build();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long datasourceId;
        private String connectorCode;
        private String connectorVersion;
        private String authProfileCode;
        private String environment;
        private Map<String, Object> endpointConfig;
        private String secretRef;
        private String tenantId;
        private String actorType;
        private String actorId;
        private String purpose;
        private String traceId;

        public Builder datasourceId(Long datasourceId) { this.datasourceId = datasourceId; return this; }
        public Builder connectorCode(String connectorCode) { this.connectorCode = connectorCode; return this; }
        public Builder connectorVersion(String connectorVersion) { this.connectorVersion = connectorVersion; return this; }
        public Builder authProfileCode(String authProfileCode) { this.authProfileCode = authProfileCode; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder endpointConfig(Map<String, Object> endpointConfig) { this.endpointConfig = endpointConfig; return this; }
        public Builder secretRef(String secretRef) { this.secretRef = secretRef; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder actorType(String actorType) { this.actorType = actorType; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }

        public ConnectionTestRequest build() {
            return new ConnectionTestRequest(datasourceId, connectorCode, connectorVersion, authProfileCode,
                    environment, endpointConfig, secretRef, tenantId, actorType, actorId, purpose, traceId);
        }
    }
}

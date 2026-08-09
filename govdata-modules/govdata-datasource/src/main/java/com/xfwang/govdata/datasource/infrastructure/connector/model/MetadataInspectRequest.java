package com.xfwang.govdata.datasource.infrastructure.connector.model;

import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretType;

import java.util.Map;
import java.util.Set;

/** 元数据探测请求。 */
public class MetadataInspectRequest {
    private final Long datasourceId;
    private final String connectorCode;
    private final String connectorVersion;
    private final String authProfileCode;
    private final Map<String, Object> endpointConfig;
    private final String secretRef;
    private final String tenantId;
    private final String actorType;
    private final String actorId;
    private final String purpose;
    private final String traceId;

    private final String catalog;
    private final String schema;

    private final String tableNamePattern;
    private final String tableType;
    private final int offset;
    private final int limit;

    public MetadataInspectRequest(Long datasourceId, String connectorCode, String connectorVersion,
                                  String authProfileCode, Map<String, Object> endpointConfig, String secretRef,
                                  String tenantId, String actorType, String actorId, String purpose,
                                  String traceId, String catalog, String schema, String tableNamePattern,
                                  String tableType, int offset, int limit) {
        this.datasourceId = datasourceId;
        this.connectorCode = connectorCode;
        this.connectorVersion = connectorVersion;
        this.authProfileCode = authProfileCode;
        this.endpointConfig = endpointConfig;
        this.secretRef = secretRef;
        this.tenantId = tenantId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.purpose = purpose;
        this.traceId = traceId;
        this.catalog = catalog;
        this.schema = schema;
        this.tableNamePattern = tableNamePattern;
        this.tableType = tableType;
        this.offset = offset;
        this.limit = limit;
    }

    public Long getDatasourceId() { return datasourceId; }
    public String getConnectorCode() { return connectorCode; }
    public String getConnectorVersion() { return connectorVersion; }
    public String getAuthProfileCode() { return authProfileCode; }
    public Map<String, Object> getEndpointConfig() { return endpointConfig; }
    public String getSecretRef() { return secretRef; }
    public String getTenantId() { return tenantId; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getPurpose() { return purpose; }
    public String getTraceId() { return traceId; }
    public String getCatalog() { return catalog; }
    public String getSchema() { return schema; }
    public String getTableNamePattern() { return tableNamePattern; }
    public String getTableType() { return tableType; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }

    public SecretResolveRequest secretRequest(SecretType secretType, Set<String> requiredKeys) {
        return SecretResolveRequest.builder()
                .tenantId(tenantId)
                .secretRef(secretRef)
                .expectedSecretType(secretType)
                .callerModule(DataSourceSecretUsage.OWNER_MODULE)
                .actorType(actorType)
                .actorId(actorId)
                .purpose(purpose)
                .traceId(traceId)
                .requiredKeys(requiredKeys)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long datasourceId;
        private String connectorCode;
        private String connectorVersion;
        private String authProfileCode;
        private Map<String, Object> endpointConfig;
        private String secretRef;
        private String tenantId;
        private String actorType;
        private String actorId;
        private String purpose;
        private String traceId;
        private String catalog;
        private String schema;
        private String tableNamePattern;
        private String tableType;
        private int offset = 0;
        private int limit = 100;

        public Builder datasourceId(Long datasourceId) { this.datasourceId = datasourceId; return this; }
        public Builder connectorCode(String connectorCode) { this.connectorCode = connectorCode; return this; }
        public Builder connectorVersion(String connectorVersion) { this.connectorVersion = connectorVersion; return this; }
        public Builder authProfileCode(String authProfileCode) { this.authProfileCode = authProfileCode; return this; }
        public Builder endpointConfig(Map<String, Object> endpointConfig) { this.endpointConfig = endpointConfig; return this; }
        public Builder secretRef(String secretRef) { this.secretRef = secretRef; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder actorType(String actorType) { this.actorType = actorType; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder catalog(String catalog) { this.catalog = catalog; return this; }
        public Builder schema(String schema) { this.schema = schema; return this; }
        public Builder tableNamePattern(String tableNamePattern) { this.tableNamePattern = tableNamePattern; return this; }
        public Builder tableType(String tableType) { this.tableType = tableType; return this; }
        public Builder offset(int offset) { this.offset = offset; return this; }
        public Builder limit(int limit) { this.limit = limit; return this; }

        public MetadataInspectRequest build() {
            return new MetadataInspectRequest(datasourceId, connectorCode, connectorVersion, authProfileCode,
                    endpointConfig, secretRef, tenantId, actorType, actorId, purpose, traceId,
                    catalog, schema, tableNamePattern, tableType, offset, limit);
        }
    }
}

package com.xfwang.govdata.datasource.application.management.view;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/** 页面可见的数据源信息；不返回凭据值。 */
public class DataSourceView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long datasourceId;
    private final Long authzResourceId;
    private final String datasourceCode;
    private final String datasourceName;
    private final String connectorCode;
    private final String connectorVersion;
    private final String authProfileCode;
    private final Map<String, Object> endpointConfig;
    private final String secretRef;
    private final String environment;
    private final Long ownerDeptId;
    private final String accessMode;
    private final String classificationCode;
    private final Integer securityLevel;
    private final String status;
    private final String lastTestStatus;
    private final LocalDateTime lastTestTime;
    private final Integer lockVersion;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;

    public DataSourceView(Long datasourceId, Long authzResourceId, String datasourceCode, String datasourceName,
                          String connectorCode, String connectorVersion, String authProfileCode,
                          Map<String, Object> endpointConfig, String secretRef, String environment,
                          Long ownerDeptId, String accessMode, String classificationCode, Integer securityLevel,
                          String status, String lastTestStatus, LocalDateTime lastTestTime, Integer lockVersion,
                          LocalDateTime createTime, LocalDateTime updateTime) {
        this.datasourceId = datasourceId;
        this.authzResourceId = authzResourceId;
        this.datasourceCode = datasourceCode;
        this.datasourceName = datasourceName;
        this.connectorCode = connectorCode;
        this.connectorVersion = connectorVersion;
        this.authProfileCode = authProfileCode;
        this.endpointConfig = endpointConfig;
        this.secretRef = secretRef;
        this.environment = environment;
        this.ownerDeptId = ownerDeptId;
        this.accessMode = accessMode;
        this.classificationCode = classificationCode;
        this.securityLevel = securityLevel;
        this.status = status;
        this.lastTestStatus = lastTestStatus;
        this.lastTestTime = lastTestTime;
        this.lockVersion = lockVersion;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public Long getDatasourceId() { return datasourceId; }
    public Long getAuthzResourceId() { return authzResourceId; }
    public String getDatasourceCode() { return datasourceCode; }
    public String getDatasourceName() { return datasourceName; }
    public String getConnectorCode() { return connectorCode; }
    public String getConnectorVersion() { return connectorVersion; }
    public String getAuthProfileCode() { return authProfileCode; }
    public Map<String, Object> getEndpointConfig() { return endpointConfig; }
    public String getSecretRef() { return secretRef; }
    public String getEnvironment() { return environment; }
    public Long getOwnerDeptId() { return ownerDeptId; }
    public String getAccessMode() { return accessMode; }
    public String getClassificationCode() { return classificationCode; }
    public Integer getSecurityLevel() { return securityLevel; }
    public String getStatus() { return status; }
    public String getLastTestStatus() { return lastTestStatus; }
    public LocalDateTime getLastTestTime() { return lastTestTime; }
    public Integer getLockVersion() { return lockVersion; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long datasourceId;
        private Long authzResourceId;
        private String datasourceCode;
        private String datasourceName;
        private String connectorCode;
        private String connectorVersion;
        private String authProfileCode;
        private Map<String, Object> endpointConfig;
        private String secretRef;
        private String environment;
        private Long ownerDeptId;
        private String accessMode;
        private String classificationCode;
        private Integer securityLevel;
        private String status;
        private String lastTestStatus;
        private LocalDateTime lastTestTime;
        private Integer lockVersion;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Builder datasourceId(Long datasourceId) { this.datasourceId = datasourceId; return this; }
        public Builder authzResourceId(Long authzResourceId) { this.authzResourceId = authzResourceId; return this; }
        public Builder datasourceCode(String datasourceCode) { this.datasourceCode = datasourceCode; return this; }
        public Builder datasourceName(String datasourceName) { this.datasourceName = datasourceName; return this; }
        public Builder connectorCode(String connectorCode) { this.connectorCode = connectorCode; return this; }
        public Builder connectorVersion(String connectorVersion) { this.connectorVersion = connectorVersion; return this; }
        public Builder authProfileCode(String authProfileCode) { this.authProfileCode = authProfileCode; return this; }
        public Builder endpointConfig(Map<String, Object> endpointConfig) { this.endpointConfig = endpointConfig; return this; }
        public Builder secretRef(String secretRef) { this.secretRef = secretRef; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder ownerDeptId(Long ownerDeptId) { this.ownerDeptId = ownerDeptId; return this; }
        public Builder accessMode(String accessMode) { this.accessMode = accessMode; return this; }
        public Builder classificationCode(String classificationCode) { this.classificationCode = classificationCode; return this; }
        public Builder securityLevel(Integer securityLevel) { this.securityLevel = securityLevel; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder lastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; return this; }
        public Builder lastTestTime(LocalDateTime lastTestTime) { this.lastTestTime = lastTestTime; return this; }
        public Builder lockVersion(Integer lockVersion) { this.lockVersion = lockVersion; return this; }
        public Builder createTime(LocalDateTime createTime) { this.createTime = createTime; return this; }
        public Builder updateTime(LocalDateTime updateTime) { this.updateTime = updateTime; return this; }

        public DataSourceView build() {
            return new DataSourceView(datasourceId, authzResourceId, datasourceCode, datasourceName, connectorCode,
                    connectorVersion, authProfileCode, endpointConfig, secretRef, environment, ownerDeptId, accessMode,
                    classificationCode, securityLevel, status, lastTestStatus, lastTestTime, lockVersion, createTime, updateTime);
        }
    }
}

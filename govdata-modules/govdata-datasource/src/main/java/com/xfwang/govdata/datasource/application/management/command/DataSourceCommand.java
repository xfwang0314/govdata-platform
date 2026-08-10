package com.xfwang.govdata.datasource.application.management.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** 数据源新增或修改命令，不包含凭据原文。 */
@Data
public class DataSourceCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long datasourceId;

    @NotBlank
    @Size(max = 100)
    private String datasourceName;

    @NotBlank
    private String connectorCode;

    @NotBlank
    private String authProfileCode;

    @NotNull
    private Map<String, Object> endpointConfig = new HashMap<>();

    @Size(max = 200)
    @Pattern(regexp = "^[a-z][a-z0-9-]*:.+$", message = "secretRef 格式无效")
    private String secretRef;

    @NotBlank
    private String environment;

    @NotNull
    private Long ownerDeptId;

    @NotNull
    private String accessMode = "READ_ONLY";

    @Min(1)
    @Max(5)
    private Integer securityLevel = 1;

    @NotBlank
    @Size(max = 32)
    private String classificationCode = "INTERNAL";

    private Integer lockVersion;

    public Long getDatasourceId() { return datasourceId; }
    public void setDatasourceId(Long datasourceId) { this.datasourceId = datasourceId; }
    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }
    public String getConnectorCode() { return connectorCode; }
    public void setConnectorCode(String connectorCode) { this.connectorCode = connectorCode; }
    public String getAuthProfileCode() { return authProfileCode; }
    public void setAuthProfileCode(String authProfileCode) { this.authProfileCode = authProfileCode; }
    public Map<String, Object> getEndpointConfig() { return endpointConfig; }
    public void setEndpointConfig(Map<String, Object> endpointConfig) { this.endpointConfig = endpointConfig; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Long getOwnerDeptId() { return ownerDeptId; }
    public void setOwnerDeptId(Long ownerDeptId) { this.ownerDeptId = ownerDeptId; }
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public Integer getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(Integer securityLevel) { this.securityLevel = securityLevel; }
    public String getClassificationCode() { return classificationCode; }
    public void setClassificationCode(String classificationCode) { this.classificationCode = classificationCode; }
    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
}

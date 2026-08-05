package com.xfwang.govdata.datasource.application.management.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
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

    @NotNull
    private Map<String, Object> endpointConfig = new HashMap<>();

    @NotBlank
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
}

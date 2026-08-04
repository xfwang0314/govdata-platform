package com.xfwang.govdata.datasource.model;

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
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "数据源编码必须以小写字母开头，只能包含小写字母、数字、下划线和短横线")
    private String datasourceCode;

    @NotBlank
    @Size(max = 100)
    private String datasourceName;

    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$")
    private String connectorCode;

    @NotNull
    private Map<String, Object> endpointConfig = new HashMap<>();

    @NotBlank
    @Size(max = 200)
    private String secretRef;

    @NotBlank
    @Pattern(regexp = "^(DEV|TEST|PROD)$")
    private String environment;

    @NotNull
    private Long ownerDeptId;

    @NotNull
    private Boolean readonlyFlag = Boolean.TRUE;

    @Min(1)
    @Max(5)
    private Integer securityLevel = 1;

    @Size(max = 32)
    private String classification = "INTERNAL";

    private Integer lockVersion;
}

package com.xfwang.govdata.datasource.application.management.view;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/** 页面可见的数据源信息；不返回凭据值。 */
@Data
@Builder
public class DataSourceView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long datasourceId;
    private Long authzResourceId;
    private String datasourceCode;
    private String datasourceName;
    private String connectorCode;
    private String connectorVersion;
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
}

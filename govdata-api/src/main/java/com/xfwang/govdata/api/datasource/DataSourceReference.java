package com.xfwang.govdata.api.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 跨模块使用的数据源稳定引用，不暴露连接配置或凭据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceReference implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long datasourceId;
    private Long resourceId;
    private Long ownerDeptId;
    private String datasourceName;
    private String connectorCode;
    private String connectorVersion;
    private String status;
}

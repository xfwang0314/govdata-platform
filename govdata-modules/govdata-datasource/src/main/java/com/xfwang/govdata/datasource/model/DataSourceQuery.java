package com.xfwang.govdata.datasource.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 数据源分页查询条件。 */
@Data
public class DataSourceQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String datasourceCode;
    private String datasourceName;
    private String connectorCode;
    private String environment;
    private String status;
}

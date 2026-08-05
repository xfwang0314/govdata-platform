package com.xfwang.govdata.api.query.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 由元数据解析的外部查询对象，物理标识只允许传给 Connector Adapter。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResourceSchema {
    private Long resourceId;
    private Long datasourceId;
    private String connectorCode;
    private String catalogName;
    private String schemaName;
    private String objectName;
    @Builder.Default private List<QueryFieldSchema> fields = new ArrayList<>();
}

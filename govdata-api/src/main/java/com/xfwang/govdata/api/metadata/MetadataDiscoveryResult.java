package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 单数据源发现完成摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataDiscoveryResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private Long harvestRunId;
    private Long datasourceId;
    private Long datasourceResourceId;
    private Long ownerDeptId;
    private String connectorCode;
    private String connectorVersion;
    private Integer namespaceCount;
    private Integer tableCount;
    private Boolean complete;
}

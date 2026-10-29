package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 元数据采集对单个数据源发起的受控发现请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataDiscoveryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long harvestRunId;
    private Long datasourceId;
    private String catalogName;
    private String schemaName;
    /** 后端限定后的最大探查表数，范围为 1 至 1000。 */
    private Integer maxTableCount;
}

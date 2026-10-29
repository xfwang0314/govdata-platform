package com.xfwang.govdata.metadata.application.catalog.query;

import lombok.Data;

/** 元数据资产筛选条件。 */
@Data
public class MetadataAssetQuery {
    private Long datasourceId;
    private Long namespaceAssetId;
    private String assetType;
    private String technicalStatus;
    private String governanceStatus;
    private String keyword;
}

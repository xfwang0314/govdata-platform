package com.xfwang.govdata.metadata.application.catalog.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 元数据目录中 Catalog、Schema、Table、Column 的树节点视图。 */
@Value
@Builder
public class MetadataAssetTreeView {
    Long assetId;
    Long parentAssetId;
    String assetCode;
    String assetType;
    String originalName;
    String displayName;
    String technicalStatus;
    String governanceStatus;
    List<MetadataAssetTreeView> children;
}

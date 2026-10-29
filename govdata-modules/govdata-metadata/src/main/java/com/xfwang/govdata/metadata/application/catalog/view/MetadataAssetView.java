package com.xfwang.govdata.metadata.application.catalog.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/** 元数据资产当前状态视图。 */
@Value
@Builder
public class MetadataAssetView {
    Long assetId;
    Long authzResourceId;
    String assetCode;
    String assetType;
    Long parentAssetId;
    Long datasourceId;
    String canonicalKey;
    String nativeObjectId;
    String originalName;
    String displayName;
    String businessName;
    String description;
    Long ownerDeptId;
    Long stewardUserId;
    String classificationCode;
    Integer securityLevel;
    String technicalStatus;
    String governanceStatus;
    LocalDateTime firstSeenTime;
    LocalDateTime lastSeenTime;
    Long lastHarvestRunId;
    String structureHash;
    String contentHash;
    Integer currentRevisionNo;
    Integer lockVersion;
    String tableType;
    String commentText;
    List<MetadataColumnView> columns;
}

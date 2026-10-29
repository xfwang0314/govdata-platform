package com.xfwang.govdata.metadata.application.authorization.event;

/** 已发布 Catalog、Schema 或表资产向统一 Resource 目录投影的事务后事件。 */
public record MetadataAssetProjectionEvent(Long assetId, String assetType, Long parentAssetId,
                                           Long datasourceResourceId, String assetName, Long ownerDeptId,
                                           String classification, Integer securityLevel,
                                           Long datasourceId, Integer revisionNo, String status) { }

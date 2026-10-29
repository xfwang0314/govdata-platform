package com.xfwang.govdata.metadata.application.impact.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** 下游影响投影视图。 */
@Value
@Builder
public class MetadataImpactView {
    Long impactProjectionId;
    String sourceModule;
    String consumerType;
    String consumerId;
    String consumerCode;
    String consumerName;
    String consumerStatus;
    Long assetId;
    Long tableAssetId;
    String dependencyType;
    LocalDateTime sourceUpdatedTime;
}

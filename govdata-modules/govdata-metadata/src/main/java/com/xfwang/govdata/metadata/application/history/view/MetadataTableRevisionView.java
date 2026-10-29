package com.xfwang.govdata.metadata.application.history.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class MetadataTableRevisionView {
    Long tableRevisionId;
    Long tableAssetId;
    Integer revisionNo;
    Integer previousRevisionNo;
    Long harvestRunId;
    String changeLevel;
    String structureHash;
    String contentHash;
    String changeSummaryJson;
    LocalDateTime observedTime;
    String actorType;
    String actorId;
    String traceId;
    List<MetadataRevisionColumnView> columns;
}

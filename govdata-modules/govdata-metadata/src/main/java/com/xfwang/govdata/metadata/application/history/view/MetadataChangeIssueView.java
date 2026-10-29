package com.xfwang.govdata.metadata.application.history.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class MetadataChangeIssueView {
    Long changeIssueId;
    String issueCode;
    String issueType;
    Long tableAssetId;
    Integer fromRevisionNo;
    Integer toRevisionNo;
    String changeLevel;
    String issueStatus;
    String changeSummaryJson;
    LocalDateTime detectedTime;
    String assigneeType;
    String assigneeId;
    LocalDateTime resolvedTime;
    String traceId;
}

package com.xfwang.govdata.metadata.harvest.application.run.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class HarvestRunFailureView {
    Long runFailureId;
    String failureScope;
    String objectKey;
    Integer attemptNo;
    String failureCode;
    String failureSummary;
    LocalDateTime occurredTime;
}

package com.xfwang.govdata.metadata.harvest.application.run.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class HarvestRunView {
    Long harvestRunId;
    String harvestRunNo;
    Long harvestPlanId;
    Long planRevisionId;
    Long datasourceId;
    String triggerType;
    String actorType;
    String actorId;
    String runStatus;
    Boolean completeRunFlag;
    Integer discoveredTableCount;
    Integer changedTableCount;
    Integer failedTableCount;
    Integer activeTableCount;
    Integer missingCandidateCount;
    Integer missingMarkedCount;
    Boolean missingSuppressedFlag;
    LocalDateTime startedTime;
    LocalDateTime completedTime;
    String traceId;
    String failureCode;
    String failureSummary;
    List<HarvestRunFailureView> failures;
}

package com.xfwang.platform.auth.application.decision.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** 授权决策审计展示模型。 */
@Value
@Builder
public class DecisionLogView {
    Long decisionId;
    String traceId;
    String subjectType;
    String subjectId;
    Long resourceId;
    String resourceType;
    String resourceCode;
    String actionCode;
    String decision;
    String reasonCode;
    String purpose;
    String policyVersionIdsJson;
    String grantIdsJson;
    String obligationSummaryJson;
    Long durationMs;
    LocalDateTime occurredTime;
}

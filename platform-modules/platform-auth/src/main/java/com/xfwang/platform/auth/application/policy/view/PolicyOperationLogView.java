package com.xfwang.platform.auth.application.policy.view;

import lombok.Builder;

import java.time.LocalDateTime;

/** 策略操作轨迹。 */
@Builder
public record PolicyOperationLogView(Long operationLogId,
                                     Long policyVersionId,
                                     String operationType,
                                     String operationLabel,
                                     String fromStatus,
                                     String toStatus,
                                     String operationReason,
                                     String operationDetailJson,
                                     String operatorType,
                                     String operatorId,
                                     String traceId,
                                     LocalDateTime occurredTime) {
}

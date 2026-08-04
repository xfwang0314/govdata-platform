package com.xfwang.platform.auth.application.model;

import lombok.Builder;
import lombok.Value;

/** 策略管理视图。 */
@Value
@Builder
public class PolicyView {
    Long policyId;
    String policyCode;
    String policyName;
    Long policyVersionId;
    Integer versionNo;
    String effect;
    String riskLevel;
    String status;
}

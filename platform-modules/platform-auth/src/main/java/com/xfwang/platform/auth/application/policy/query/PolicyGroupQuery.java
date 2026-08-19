package com.xfwang.platform.auth.application.policy.query;

import lombok.Data;

/** 策略组筛选条件。 */
@Data
public class PolicyGroupQuery {
    private String keyword;
    private String status;
    private String riskLevel;
    private Boolean deleted = false;
}

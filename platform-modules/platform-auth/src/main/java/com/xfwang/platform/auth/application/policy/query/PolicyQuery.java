package com.xfwang.platform.auth.application.policy.query;

import lombok.Data;

/** ABAC 策略筛选条件。 */
@Data
public class PolicyQuery {
    private String keyword;
    private String status;
    private String riskLevel;
}

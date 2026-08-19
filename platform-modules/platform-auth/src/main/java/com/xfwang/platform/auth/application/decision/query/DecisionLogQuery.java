package com.xfwang.platform.auth.application.decision.query;

import lombok.Data;

/** 授权决策日志筛选条件。 */
@Data
public class DecisionLogQuery {
    private String traceId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String decision;
    private String actionCode;
}

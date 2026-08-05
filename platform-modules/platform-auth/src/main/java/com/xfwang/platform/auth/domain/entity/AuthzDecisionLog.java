package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 授权决策审计日志。
 */
@Data
@TableName("authz_decision_log")
public class AuthzDecisionLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long decisionId;

    private String tenantId;

    private String subjectTenantId;

    private String subjectType;

    private String subjectId;

    private String subjectSnapshotJson;

    private String resourceTenantId;

    private Long resourceId;

    private String resourceTypeSnapshot;

    private String resourceCodeSnapshot;

    private String actionCode;

    private String decision;

    private String reasonCode;

    private String policyVersionIdsJson;

    private String grantIdsJson;

    private Long delegationId;

    private Long breakGlassAccessId;

    private String obligationSummaryJson;

    private String purpose;

    private String traceId;

    private Long durationMs;

    private LocalDateTime occurredTime;
}

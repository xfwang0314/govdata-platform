package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 追加写入的策略生命周期操作日志。 */
@Data
@TableName("authz_policy_operation_log")
public class AuthzPolicyOperationLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long operationLogId;
    private String tenantId;
    private Long policyGroupId;
    private String policyGroupCode;
    private Long policyVersionId;
    private String operationType;
    private String fromStatus;
    private String toStatus;
    private String operationReason;
    private String operationDetailJson;
    private String beforeContentHash;
    private String afterContentHash;
    private String operatorType;
    private String operatorId;
    private String traceId;
    private LocalDateTime occurredTime;
}

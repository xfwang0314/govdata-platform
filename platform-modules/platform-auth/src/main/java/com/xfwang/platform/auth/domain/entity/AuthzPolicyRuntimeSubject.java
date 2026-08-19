package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 当前生效策略版本的主体候选索引。 */
@Data
@TableName("authz_policy_runtime_subject")
public class AuthzPolicyRuntimeSubject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long runtimeSubjectId;
    private String tenantId;
    private Long policyGroupId;
    private Long policyGroupVersionId;
    private String subjectType;
    private String subjectId;
    private Boolean includeChildren;
    private Long createBy;
    private LocalDateTime createTime;
}

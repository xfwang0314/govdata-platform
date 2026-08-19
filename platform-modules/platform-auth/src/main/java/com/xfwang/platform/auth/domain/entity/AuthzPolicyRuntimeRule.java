package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 当前生效策略版本编译得到的规则索引，可从版本文档完整重建。 */
@Data
@TableName("authz_policy_runtime_rule")
public class AuthzPolicyRuntimeRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long runtimeRuleId;
    private String tenantId;
    private Long policyGroupId;
    private Long policyGroupVersionId;
    private String ruleCode;
    private String effect;
    private Integer priority;
    private String resourceType;
    private String resourceScope;
    private String actionCodesJson;
    private String purposeCodesJson;
    private String conditionJson;
    private String obligationJson;
    private Long createBy;
    private LocalDateTime createTime;
}

package com.xfwang.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 不可变 ABAC 策略版本。
 */
@Data
@TableName("authz_policy_version")
public class AuthzPolicyVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long policyVersionId;
    private String tenantId;
    private Long policyId;
    private Integer versionNo;
    private String effect;
    private String conditionJson;
    private String obligationJson;
    private Integer priority;
    private String versionStatus;
    private String contentHash;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long publishedBy;
    private LocalDateTime publishedTime;
}

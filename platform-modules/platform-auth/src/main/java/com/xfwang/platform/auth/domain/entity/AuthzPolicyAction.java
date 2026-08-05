package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略版本动作绑定。
 */
@Data
@TableName("authz_policy_action")
public class AuthzPolicyAction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long bindingId;
    private String tenantId;
    private Long policyVersionId;
    private String actionCode;
    private Long createBy;
    private LocalDateTime createTime;
}

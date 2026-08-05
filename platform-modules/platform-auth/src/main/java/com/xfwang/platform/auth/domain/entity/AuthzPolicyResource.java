package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略资源绑定。
 */
@Data
@TableName("authz_policy_resource")
public class AuthzPolicyResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long bindingId;

    private String tenantId;

    private Long policyVersionId;

    /**
     * 为空时可按 resourceType 绑定一类资源。
     */
    private Long resourceId;

    private String resourceType;

    private Boolean includeChildren;

    private Long createBy;

    private LocalDateTime createTime;
}

package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 当前生效规则指定的资源实例索引；类型级规则不产生记录。 */
@Data
@TableName("authz_policy_runtime_resource")
public class AuthzPolicyRuntimeResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long runtimeResourceId;
    private String tenantId;
    private Long runtimeRuleId;
    private Long resourceId;
    private Boolean includeChildren;
    private Long createBy;
    private LocalDateTime createTime;
}

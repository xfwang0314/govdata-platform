package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Grant 的资源、动作和附加约束范围。
 */
@Data
@TableName("authz_grant_scope")
public class AuthzGrantScope implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long scopeId;
    private String tenantId;
    private Long grantId;
    private Long resourceId;
    private String actionCode;
    private Boolean includeChildren;
    private String obligationJson;
    private Long createBy;
    private LocalDateTime createTime;
}

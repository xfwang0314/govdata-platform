package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/** 策略生命周期与版本管理的稳定聚合容器。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("authz_policy_group")
public class AuthzPolicyGroup extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long policyGroupId;
    private String policyGroupCode;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long currentVersionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long draftVersionId;
    private Integer nextVersionNo;
    private String creationSource;
    private String sourceTemplateCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deletedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime deletedTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deleteReason;
    @Version
    private Integer lockVersion;
    private String delFlag;
}

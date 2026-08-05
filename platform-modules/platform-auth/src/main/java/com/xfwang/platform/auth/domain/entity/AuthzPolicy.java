package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * ABAC 策略聚合根，策略内容由不可变版本承载。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("authz_policy")
public class AuthzPolicy extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long policyId;

    private String policyCode;

    private String policyName;

    private Long currentVersionId;

    private String riskLevel;

    private String status;

    @Version
    private Integer lockVersion;

    @TableLogic
    private String delFlag;
}

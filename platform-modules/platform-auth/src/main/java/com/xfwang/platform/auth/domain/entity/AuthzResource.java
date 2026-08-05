package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 统一可授权资源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("authz_resource")
public class AuthzResource extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long resourceId;

    private String resourceCode;

    private String resourceName;

    private String resourceType;

    private Long parentId;

    private String businessId;

    private String sourceModule;

    private Long ownerDeptId;

    private String regionCode;

    @TableField("classification_code")
    private String classification;

    private Integer securityLevel;

    private String attributesJson;

    private Boolean inheritPolicy;

    private String status;

    @Version
    private Integer lockVersion;

    @TableLogic
    private String delFlag;
}

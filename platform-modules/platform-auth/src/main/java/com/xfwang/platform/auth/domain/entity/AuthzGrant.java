package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 租户内部的资源授权凭据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("authz_grant")
public class AuthzGrant extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long grantId;

    private String grantCode;

    private String sourceType;

    private String sourceId;

    private String grantorType;

    private String grantorId;

    private String granteeType;

    private String granteeId;

    private String purpose;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private String conditionJson;

    private String status;

    private Long revokedBy;

    private LocalDateTime revokedTime;

    private String revokeReason;

    @Version
    private Integer lockVersion;

    @TableLogic
    private String delFlag;
}

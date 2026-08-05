package com.xfwang.platform.secret.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_secret")
public class PlatformSecret extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long secretId;
    private String secretRef;
    private String secretCode;
    private String secretName;
    private String description;
    private String secretType;
    private String ownerModule;
    private String purposeScope;
    private Long currentVersionId;
    private String status;
    private LocalDateTime expireTime;
    @Version
    private Integer lockVersion;
    @TableLogic
    private String delFlag;
}

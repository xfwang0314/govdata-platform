package com.xfwang.platform.secret.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_secret_version")
public class PlatformSecretVersion extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long secretVersionId;
    private Long secretId;
    private Integer versionNo;
    @TableField("secret_ciphertext")
    private String ciphertext;
    private String encryptionAlgorithm;
    private String keyVersion;
    private String checksum;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String status;
}

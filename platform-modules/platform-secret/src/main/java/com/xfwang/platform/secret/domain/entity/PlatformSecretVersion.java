package com.xfwang.platform.secret.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 平台凭据不可变密文版本实体类。
 * <p>
 * 对应数据库表 {@code platform_secret_version}。保存加解密后的密文字符串、加密时所用的算法名称、主密钥版本以及密文校验和摘要。
 * 凭据更新/轮换时只插入新版本记录，历史版本保留供追溯或平滑过渡。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_secret_version")
public class PlatformSecretVersion extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 凭据版本主键雪花 ID
     */
    @TableId
    private Long secretVersionId;

    /**
     * 关联的凭据主表 ID (platform_secret.secret_id)
     */
    private Long secretId;

    /**
     * 业务版本号，从 1 递增（1, 2, 3...）
     */
    private Integer versionNo;

    /**
     * 加密后的密文字符串（Base64 编码，包含随机 Nonce 与 GCM Tag，不存主密钥）
     */
    @TableField("secret_ciphertext")
    private String ciphertext;

    /**
     * 生成此版本密文时使用的算法编码（如 "AES-256-GCM"、"SM4-GCM"）
     */
    private String encryptionAlgorithm;

    /**
     * 加密此版本时使用的外部主密钥版本标识（如 "v1"、"v2"）
     */
    private String keyVersion;

    /**
     * 密文内容的 SHA-256 校验和摘要，用于检测防篡改
     */
    private String checksum;

    /**
     * 该密文版本的生效时间
     */
    private LocalDateTime effectiveTime;

    /**
     * 该密文版本的失效/过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 该密文版本的状态：ACTIVE（当前有效）、DISABLED（历史失效）
     */
    private String status;
}

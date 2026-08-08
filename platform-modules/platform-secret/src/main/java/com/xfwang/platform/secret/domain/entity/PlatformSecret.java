package com.xfwang.platform.secret.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 平台凭据元数据实体类。
 * <p>
 * 对应数据库表 {@code platform_secret}。只保存凭据句柄、类型、拥有模块与允许用途范围等元数据，
 * 绝不保存任何凭据密文或明文信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_secret")
public class PlatformSecret extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 凭据主键雪花 ID
     */
    @TableId
    private Long secretId;

    /**
     * 业务模块保存的稳定凭据引用句柄，格式如 "secret:sec_xxx"
     */
    private String secretRef;

    /**
     * 租户内稳定凭据编码，由服务端根据 ID 自动生成（如 "sec_8899"）
     */
    private String secretCode;

    /**
     * 凭据名称
     */
    private String secretName;

    /**
     * 凭据用途说明、责任边界与维护注意事项（不得填写任何敏感值）
     */
    private String description;

    /**
     * 凭据稳定类型编码（对应 SecretType 枚举，如 USERNAME_PASSWORD、TOKEN）
     */
    private String secretType;

    /**
     * 允许解析该凭据的拥有模块编码（如 "govdata-datasource"）
     */
    private String ownerModule;

    /**
     * 允许用途范围编码，逗号分隔或 "*" 表示通配全部用途
     */
    private String purposeScope;

    /**
     * 当前激活生效的密文版本主键（指向 platform_secret_version.secret_version_id）
     */
    private Long currentVersionId;

    /**
     * 凭据全局状态：ACTIVE（启用）、DISABLED（禁用）
     */
    private String status;

    /**
     * 凭据过期时间（到达该时间后阻止解析）
     */
    private LocalDateTime expireTime;

    /**
     * 乐观锁版本号（配合 MyBatis-Plus @Version 插件防止并发覆盖）
     */
    @Version
    private Integer lockVersion;

    /**
     * 逻辑删除标志：0-正常，1-已删除
     */
    @TableLogic
    private String delFlag;
}

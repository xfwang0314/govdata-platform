package com.xfwang.platform.secret.application.management.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 凭据管理前端响应视图对象（VO）。
 * <p>
 * 仅暴露凭据元数据与配置状态，绝对不包含任何敏感密文或明文信息。
 */
@Value
@Builder
public class SecretView {
    /** 凭据主键雪花 ID */
    Long secretId;
    /** 稳定凭据引用句柄（如 "secret:sec_xxx"） */
    String secretRef;
    /** 凭据名称 */
    String secretName;
    /** 描述说明 */
    String description;
    /** 凭据类型编码 */
    String secretType;
    /** 所属业务模块 */
    String ownerModule;
    /** 允许用途范围 */
    String purposeScope;
    /** 状态（ACTIVE / DISABLED） */
    String status;
    /** 过期时间 */
    LocalDateTime expireTime;
    /** 当前激活的版本号（1, 2, 3...） */
    Integer currentVersionNo;
    /** 乐观锁版本号 */
    Integer lockVersion;
    /** 创建时间 */
    LocalDateTime createTime;
    /** 修改时间 */
    LocalDateTime updateTime;
    /** 是否已配置密文版本 */
    boolean configured;
}

package com.xfwang.platform.secret.application.management.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭据元数据修改 Command 请求对象。
 * <p>
 * 仅用于修改凭据名称、描述说明、允许用途范围及过期时间，不修改加密密文。
 */
@Data
public class SecretUpdateCommand {

    /**
     * 凭据名称
     */
    @NotBlank
    @Size(max = 100)
    private String secretName;

    /**
     * 凭据描述说明
     */
    @Size(max = 500)
    private String description;

    /**
     * 允许用途范围编码
     */
    @NotBlank
    @Size(max = 500)
    private String purposeScope;

    /**
     * 凭据过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 当前乐观锁版本号
     */
    @NotNull
    private Integer lockVersion;
}

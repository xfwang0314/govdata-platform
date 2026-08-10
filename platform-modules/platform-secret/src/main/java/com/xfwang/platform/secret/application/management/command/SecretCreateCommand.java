package com.xfwang.platform.secret.application.management.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 凭据创建 Command 请求对象。
 * <p>
 * {@code @ToString(exclude = "values")} 排除 values 字段打印，防止敏感明文泄露在日志中。
 */
@Data
@ToString(exclude = "values")
public class SecretCreateCommand {

    /**
     * 凭据名称
     */
    @NotBlank
    @Size(max = 100)
    private String secretName;

    /**
     * 凭据描述、责任边界与维护说明（不得填写敏感值）
     */
    @Size(max = 500)
    private String description;

    /**
     * 凭据类型编码（如 USERNAME_PASSWORD、TOKEN）
     */
    @NotBlank
    private String secretType;

    /**
     * 允许解析该凭据的拥有模块编码（如 "govdata-datasource"）
     */
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$")
    private String ownerModule;

    /**
     * 允许用途范围编码，逗号分隔或 "*"
     */
    @NotBlank
    @Size(max = 500)
    private String purposeScope;

    /**
     * 凭据字段明文 Map（如 {"username":"root", "password":"xxx"}）
     */
    @NotEmpty
    private Map<@Pattern(regexp = "^[a-z][a-zA-Z0-9_]{0,63}$") String, @NotBlank String> values;

    /**
     * 凭据过期时间（可选）
     */
    private LocalDateTime expireTime;
}

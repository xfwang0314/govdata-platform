package com.xfwang.platform.secret.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 凭据全局加密属性配置类。
 * <p>
 * 绑定配置文件中的 {@code platform.secret.encryption} 前缀，包含主密钥、主密钥版本和默认加密算法配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.secret.encryption")
public class SecretEncryptionProperties {

    /**
     * Base64 编码的 32 字节 AES/SM4 外部注入主密钥。
     * <p>
     * 严禁存入数据库或 Git，由环境变量 {@code PLATFORM_SECRET_MASTER_KEY} 在启动时外部注入。
     */
    private String masterKey;

    /**
     * 当前使用的主密钥版本标识（如 "v1"、"v2"）。
     */
    private String keyVersion = "v1";

    /**
     * 默认使用的对称加密算法编码。
     * <p>
     * 支持选项：{@code AES-256-GCM}（默认）或 {@code SM4-GCM}（国密）。
     */
    private String algorithm = "AES-256-GCM";
}

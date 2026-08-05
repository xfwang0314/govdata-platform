package com.xfwang.platform.secret.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "platform.secret.encryption")
public class SecretEncryptionProperties {
    /** Base64 编码的 32 字节 AES 主密钥。 */
    private String masterKey;
    private String keyVersion = "v1";
}

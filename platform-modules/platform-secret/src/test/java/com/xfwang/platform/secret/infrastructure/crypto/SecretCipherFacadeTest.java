package com.xfwang.platform.secret.infrastructure.crypto;

import com.xfwang.platform.secret.infrastructure.config.SecretEncryptionProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class SecretCipherFacadeTest {

    @Test
    void supportsDynamicAlgorithmRoutingForDecryption() {
        SecretEncryptionProperties properties = new SecretEncryptionProperties();
        properties.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setAlgorithm("SM4-GCM");

        AesGcmSecretCipher aesCipher = new AesGcmSecretCipher(properties);
        Sm4GcmSecretCipher sm4Cipher = new Sm4GcmSecretCipher(properties);
        SecretCipherFacade facade = new SecretCipherFacade(properties, List.of(aesCipher, sm4Cipher));

        // 1. 历史 AES 密文解密验证（算法指定为 AES-256-GCM）
        String aesCiphertext = aesCipher.encrypt("{\"password\":\"aes_secret\"}", "000000|1|1");
        assertThat(facade.decrypt("AES-256-GCM", aesCiphertext, "000000|1|1"))
            .isEqualTo("{\"password\":\"aes_secret\"}");

        // 2. 当前配置 SM4 密文加密解密验证
        String sm4Ciphertext = facade.encrypt("{\"password\":\"sm4_secret\"}", "000000|1|2");
        assertThat(facade.decrypt("SM4-GCM", sm4Ciphertext, "000000|1|2"))
            .isEqualTo("{\"password\":\"sm4_secret\"}");
    }
}

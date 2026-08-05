package com.xfwang.platform.secret.infrastructure.crypto;

import com.xfwang.platform.secret.infrastructure.config.SecretEncryptionProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AesGcmSecretCipherTest {

    @Test
    void encryptsWithRandomNonceAndAuthenticatesAssociatedData() {
        SecretEncryptionProperties properties = new SecretEncryptionProperties();
        properties.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(properties);

        String first = cipher.encrypt("{\"password\":\"secret\"}", "000000|1|1");
        String second = cipher.encrypt("{\"password\":\"secret\"}", "000000|1|1");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first, "000000|1|1")).isEqualTo("{\"password\":\"secret\"}");
        assertThatThrownBy(() -> cipher.decrypt(first, "000000|1|2")).isInstanceOf(RuntimeException.class);
    }
}

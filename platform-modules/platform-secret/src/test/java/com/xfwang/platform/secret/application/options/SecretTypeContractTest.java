package com.xfwang.platform.secret.application.options;

import com.xfwang.platform.api.secret.SecretType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretTypeContractTest {

    @Test
    void exposesStableCodesLabelsAndMinimumRequiredFields() {
        assertThat(SecretType.values()).extracting(SecretType::code)
            .containsExactly("USERNAME_PASSWORD", "PASSWORD", "TOKEN", "API_KEY", "SSH_PRIVATE_KEY",
                "OAUTH2_CLIENT", "KEY_VALUE");
        assertThat(SecretType.USERNAME_PASSWORD.label()).isEqualTo("用户名和密码");
        assertThat(SecretType.USERNAME_PASSWORD.requiredKeys())
            .containsExactlyInAnyOrder("username", "password");
        assertThat(SecretType.PASSWORD.requiredKeys()).containsExactly("password");
        assertThat(SecretType.TOKEN.requiredKeys()).containsExactly("token");
        assertThat(SecretType.API_KEY.requiredKeys()).containsExactly("apiKey");
        assertThat(SecretType.SSH_PRIVATE_KEY.requiredKeys()).containsExactlyInAnyOrder("username", "privateKey");
        assertThat(SecretType.SSH_PRIVATE_KEY.allowedKeys()).contains("passphrase");
        assertThat(SecretType.OAUTH2_CLIENT.requiredKeys()).containsExactlyInAnyOrder("clientId", "clientSecret");
        assertThat(SecretType.KEY_VALUE.requiredKeys()).isEmpty();
        assertThat(SecretType.KEY_VALUE.dynamicFieldsAllowed()).isTrue();
    }
}

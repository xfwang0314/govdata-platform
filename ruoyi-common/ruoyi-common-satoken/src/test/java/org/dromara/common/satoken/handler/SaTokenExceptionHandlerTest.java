package org.dromara.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenExceptionHandlerTest {

    @Test
    void frozenTokenReasonDoesNotExposeTokenValue() {
        String token = "sensitive-token-value";
        NotLoginException exception = new NotLoginException("token 已被冻结：" + token,
            "login", NotLoginException.TOKEN_FREEZE);

        assertThat(SaTokenExceptionHandler.safeAuthenticationReason(exception))
            .isEqualTo(NotLoginException.TOKEN_FREEZE)
            .doesNotContain(token);
    }
}

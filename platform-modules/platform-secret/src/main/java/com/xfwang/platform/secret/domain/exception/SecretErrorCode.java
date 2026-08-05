package com.xfwang.platform.secret.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

@Getter
@AllArgsConstructor
public enum SecretErrorCode implements ErrorCode {
    NOT_FOUND("GOV-SECRET-0404", "凭据不存在", 404),
    CODE_DUPLICATE("GOV-SECRET-0409", "凭据编码已存在", 409),
    CONFIGURATION_MISSING("GOV-SECRET-0501", "凭据主加密密钥未配置", 503),
    UNAVAILABLE("GOV-SECRET-0502", "凭据当前不可用", 503);

    private final String code;
    private final String message;
    private final int httpStatus;
}

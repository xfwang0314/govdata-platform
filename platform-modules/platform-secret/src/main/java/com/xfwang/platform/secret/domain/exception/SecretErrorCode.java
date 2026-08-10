package com.xfwang.platform.secret.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/**
 * 凭据模块领域异常错误码枚举。
 */
@Getter
@AllArgsConstructor
public enum SecretErrorCode implements ErrorCode {

    /** 404: 请求的凭据未找到 */
    NOT_FOUND("GOV-SECRET-0404", "凭据不存在", 404),

    /** 409: 凭据编码已存在冲突 */
    CODE_DUPLICATE("GOV-SECRET-0409", "凭据编码已存在", 409),

    /** 503: 平台主加密密钥（PLATFORM_SECRET_MASTER_KEY）未配置或非法 */
    CONFIGURATION_MISSING("GOV-SECRET-0501", "凭据主加密密钥未配置", 503),

    /** 503: 凭据处于已停用、已过期、版本失效或解密异常等不可用状态 */
    UNAVAILABLE("GOV-SECRET-0502", "凭据当前不可用", 503);

    private final String code;
    private final String message;
    private final int httpStatus;
}

package com.xfwang.platform.auth.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/** 平台授权模块稳定错误编码。 */
@Getter
@AllArgsConstructor
public enum AuthorizationErrorCode implements ErrorCode {

    ACCESS_DENIED("GOV-AUTH-0403", "当前主体无权访问该资源", 403),
    RESOURCE_NOT_FOUND("GOV-AUTH-0404", "授权资源不存在", 404),
    RESOURCE_CONTEXT_INVALID("GOV-AUTH-0400-01", "授权资源上下文无效", 400),
    RESOURCE_PROJECTION_UNAVAILABLE("GOV-AUTH-0409-01", "授权资源投影尚未就绪", 409),
    TENANT_MISMATCH("GOV-AUTH-0403-01", "禁止跨租户访问授权资源", 403),
    RESOURCE_DISABLED("GOV-AUTH-0403-02", "授权资源已停用", 403),
    REQUEST_INVALID("GOV-AUTH-0400-02", "授权请求无效", 400);

    private final String code;
    private final String message;
    private final int httpStatus;
}

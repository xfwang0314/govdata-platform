package org.dromara.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 平台通用异常编码。
 *
 * <p>业务模块应使用独立前缀扩展，例如 GOV-META-xxxx、GOV-EXCHANGE-xxxx。</p>
 */
@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    SUCCESS("0", "操作成功", 200),
    VALIDATION_ERROR("GOV-COMMON-0400", "请求参数错误", 400),
    UNAUTHORIZED("GOV-COMMON-0401", "认证失败", 401),
    FORBIDDEN("GOV-COMMON-0403", "无权访问", 403),
    NOT_FOUND("GOV-COMMON-0404", "资源不存在", 404),
    METHOD_NOT_ALLOWED("GOV-COMMON-0405", "请求方法不支持", 405),
    BUSINESS_ERROR("GOV-COMMON-0500", "业务处理失败", 500),
    SYSTEM_ERROR("GOV-COMMON-0501", "系统内部错误", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    public static GlobalErrorCode fromHttpStatus(int status) {
        for (GlobalErrorCode value : values()) {
            if (value.httpStatus == status && value != SUCCESS) {
                return value;
            }
        }
        return BUSINESS_ERROR;
    }
}

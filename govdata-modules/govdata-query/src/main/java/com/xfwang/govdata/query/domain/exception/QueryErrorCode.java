package com.xfwang.govdata.query.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/** govdata-query 稳定异常编码。 */
@Getter
@AllArgsConstructor
public enum QueryErrorCode implements ErrorCode {
    INVALID_REQUEST("GOV-QUERY-0400", "查询请求无效", 400),
    AUTHORIZATION_REQUIRED("GOV-QUERY-0403", "缺少有效资源授权", 403),
    FIELD_FORBIDDEN("GOV-QUERY-0403-01", "查询字段未被授权", 403),
    SCHEMA_NOT_FOUND("GOV-QUERY-0404", "查询资源元数据不存在", 404),
    ADAPTER_NOT_FOUND("GOV-QUERY-0501", "没有可用的查询执行适配器", 500),
    EXECUTION_FAILED("GOV-QUERY-0502", "外部数据查询失败", 500);

    private final String code;
    private final String message;
    private final int httpStatus;
}

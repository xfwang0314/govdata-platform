package com.xfwang.govdata.datasource.domain.exception;

import org.dromara.common.core.exception.ErrorCode;

/** 数据源模块稳定异常编码。 */
public enum DataSourceErrorCode implements ErrorCode {
    QUERY_FORBIDDEN("GOV-DS-0403-QUERY", "缺少有效的只读查询授权", 403),
    QUERY_INVALID("GOV-DS-0400-QUERY", "查询计划或连接配置无效", 400),
    QUERY_BUSY("GOV-DS-0429-QUERY", "查询并发已达上限，请稍后重试", 429),
    QUERY_TIMEOUT("GOV-DS-0504-QUERY", "外部查询超时", 504),
    QUERY_TOO_LARGE("GOV-DS-0413-QUERY", "查询结果超过大小限制", 413),
    QUERY_FAILED("GOV-DS-0502-QUERY", "外部查询执行失败", 502),
    NOT_FOUND("GOV-DS-0404", "数据源不存在", 404),
    CODE_DUPLICATE("GOV-DS-0409", "数据源编码已存在", 409),
    CONNECTOR_UNAVAILABLE("GOV-DS-0501", "没有可用的 Connector", 501),
    SECRET_UNAVAILABLE("GOV-DS-0502", "凭据引用无法解析", 503),
    CONNECTION_FAILED("GOV-DS-0503", "数据源连接失败", 502);

    private final String code;
    private final String message;
    private final int httpStatus;

    DataSourceErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() { return code; }
    @Override
    public String getMessage() { return message; }
    @Override
    public int getHttpStatus() { return httpStatus; }
}

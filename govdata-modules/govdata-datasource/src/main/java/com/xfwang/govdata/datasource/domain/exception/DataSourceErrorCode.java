package com.xfwang.govdata.datasource.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/** 数据源模块稳定异常编码。 */
@Getter
@AllArgsConstructor
public enum DataSourceErrorCode implements ErrorCode {
    NOT_FOUND("GOV-DS-0404", "数据源不存在", 404),
    CODE_DUPLICATE("GOV-DS-0409", "数据源编码已存在", 409),
    CONNECTOR_UNAVAILABLE("GOV-DS-0501", "没有可用的 Connector", 501),
    SECRET_UNAVAILABLE("GOV-DS-0502", "凭据引用无法解析", 503),
    CONNECTION_FAILED("GOV-DS-0503", "数据源连接失败", 502);

    private final String code;
    private final String message;
    private final int httpStatus;
}

package org.dromara.common.core.exception;

/**
 * 稳定异常编码契约。
 */
public interface ErrorCode {

    String getCode();

    String getMessage();

    int getHttpStatus();
}

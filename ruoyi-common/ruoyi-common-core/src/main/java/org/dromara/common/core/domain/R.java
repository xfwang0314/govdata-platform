package org.dromara.common.core.domain;

import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.exception.ErrorCode;
import org.dromara.common.core.exception.GlobalErrorCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.io.Serial;
import java.io.Serializable;

/**
 * 响应信息主体
 *
 * @author Lion Li
 */
@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成功
     */
    public static final int SUCCESS = 200;

    /**
     * 失败
     */
    public static final int FAIL = 500;

    private int code;

    private String msg;

    private T data;

    /**
     * 稳定异常编码；成功时为 0。
     */
    private String errorCode;

    /**
     * 请求链路标识，可用于关联服务端日志。
     */
    private String traceId;

    public static <T> R<T> ok() {
        return restResult(null, SUCCESS, "操作成功");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, SUCCESS, "操作成功");
    }

    public static <T> R<T> ok(String msg) {
        return restResult(null, SUCCESS, msg);
    }

    public static <T> R<T> ok(String msg, T data) {
        return restResult(data, SUCCESS, msg);
    }

    public static <T> R<T> fail() {
        return restResult(null, FAIL, "操作失败");
    }

    public static <T> R<T> fail(String msg) {
        return restResult(null, FAIL, msg);
    }

    public static <T> R<T> fail(T data) {
        return restResult(data, FAIL, "操作失败");
    }

    public static <T> R<T> fail(String msg, T data) {
        return restResult(data, FAIL, msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        return restResult(null, code, msg, GlobalErrorCode.fromHttpStatus(code).getCode());
    }

    public static <T> R<T> fail(int code, String errorCode, String msg) {
        return restResult(null, code, msg, errorCode);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String msg) {
        return restResult(null, errorCode.getHttpStatus(), msg, errorCode.getCode());
    }

    /**
     * 返回警告消息
     *
     * @param msg 返回内容
     * @return 警告消息
     */
    public static <T> R<T> warn(String msg) {
        return restResult(null, HttpStatus.WARN, msg);
    }

    /**
     * 返回警告消息
     *
     * @param msg 返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static <T> R<T> warn(String msg, T data) {
        return restResult(data, HttpStatus.WARN, msg);
    }

    private static <T> R<T> restResult(T data, int code, String msg) {
        String errorCode = code == SUCCESS
            ? GlobalErrorCode.SUCCESS.getCode() : GlobalErrorCode.BUSINESS_ERROR.getCode();
        return restResult(data, code, msg, errorCode);
    }

    private static <T> R<T> restResult(T data, int code, String msg, String errorCode) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        r.setErrorCode(errorCode);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    public static <T> Boolean isError(R<T> ret) {
        return !isSuccess(ret);
    }

    public static <T> Boolean isSuccess(R<T> ret) {
        return R.SUCCESS == ret.getCode();
    }
}

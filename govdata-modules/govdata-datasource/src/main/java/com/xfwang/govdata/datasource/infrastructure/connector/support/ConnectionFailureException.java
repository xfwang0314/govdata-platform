package com.xfwang.govdata.datasource.infrastructure.connector.support;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;

/**
 * Adapter 内部携带稳定原因码的受控失败，不包含外部异常原文。
 */
public class ConnectionFailureException extends RuntimeException {

    private final ConnectionTestReason reason;

    public ConnectionFailureException(ConnectionTestReason reason) {
        super(reason.code());
        this.reason = reason;
    }

    public ConnectionTestReason reason() {
        return reason;
    }
}

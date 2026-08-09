package com.xfwang.govdata.datasource.domain.enums;

/** 连接测试对外返回和持久化的稳定原因码。 */
public enum ConnectionTestReason {
    CONNECTED("连接成功"),
    ENDPOINT_INVALID("连接端点配置无效"),
    SECRET_UNAVAILABLE("凭据不可用"),
    SECRET_FIELDS_MISSING("凭据缺少必要字段"),
    DRIVER_UNAVAILABLE("连接驱动不可用"),
    AUTHENTICATION_FAILED("认证失败，请检查用户名和密码"),
    DATABASE_NOT_FOUND("目标数据库不存在或无权访问"),
    CONNECTION_TIMEOUT("连接超时，请检查网络和超时配置"),
    CONNECTION_REFUSED("目标服务拒绝连接，请检查地址、端口和监听配置"),
    HOST_UNREACHABLE("无法解析或访问目标主机"),
    TLS_HANDSHAKE_FAILED("TLS 握手失败，请检查协议和证书配置"),
    HTTP_STATUS_REJECTED("HTTP 服务返回非成功状态，请检查健康检查路径和授权"),
    OAUTH2_TOKEN_FAILED("OAuth2 Token 获取失败，请检查客户端凭据和 Token Endpoint"),
    SSH_HOST_KEY_MISMATCH("SFTP 主机指纹不匹配"),
    REMOTE_PATH_UNAVAILABLE("远程目录不存在或无权读取"),
    CONNECTION_INVALID("连接建立后校验失败"),
    PING_FAILED("连接建立后 PING 校验失败"),
    CONNECTION_FAILED("连接失败，请检查端点、凭据、驱动和网络");

    private final String message;

    ConnectionTestReason(String message) {
        this.message = message;
    }

    public String code() {
        return name();
    }

    public String message() {
        return message;
    }
}

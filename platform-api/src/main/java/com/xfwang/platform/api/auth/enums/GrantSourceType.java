package com.xfwang.platform.api.auth.enums;

/** AuthorizationGrant 的稳定业务来源类型。 */
public enum GrantSourceType {
    SHARING("数据共享"),
    ADMIN("管理员授权"),
    SUBSCRIPTION("服务订阅"),
    DELEGATION("Agent 委托"),
    SYSTEM("系统授权");

    private final String label;

    GrantSourceType(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}

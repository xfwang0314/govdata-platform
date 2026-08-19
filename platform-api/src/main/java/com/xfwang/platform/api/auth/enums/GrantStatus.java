package com.xfwang.platform.api.auth.enums;

/**
 * Grant 生命周期状态。
 */
public enum GrantStatus {

    DRAFT("DRAFT", "草稿"),

    PENDING("PENDING", "待激活"),

    ACTIVE("ACTIVE", "生效中"),

    EXPIRED("EXPIRED", "已过期"),

    REVOKED("REVOKED", "已撤销");

    private final String code;
    private final String label;

    GrantStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}

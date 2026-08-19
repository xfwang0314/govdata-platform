package com.xfwang.platform.api.auth.enums;

/**
 * 授权决策结果。
 */
public enum DecisionType {

    PERMIT("允许"),

    DENY("拒绝");

    private final String label;

    DecisionType(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}

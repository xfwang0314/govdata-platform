package com.xfwang.platform.api.auth.enums;

/** 策略组内容的创建来源；不参与运行时授权决策。 */
public enum PolicyGroupSourceType {

    CUSTOM("自定义策略");

    private final String label;

    PolicyGroupSourceType(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}

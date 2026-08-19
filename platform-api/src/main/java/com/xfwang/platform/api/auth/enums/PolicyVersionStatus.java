package com.xfwang.platform.api.auth.enums;

/** 不可变策略版本生命周期。 */
public enum PolicyVersionStatus {

    DRAFT("待发布"),
    PUBLISHED("已发布"),
    SUPERSEDED("已被替代"),
    DISCARDED("已放弃");

    private final String label;

    PolicyVersionStatus(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}

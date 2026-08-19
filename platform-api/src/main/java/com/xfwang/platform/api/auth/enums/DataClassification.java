package com.xfwang.platform.api.auth.enums;

import java.util.Arrays;
import java.util.Optional;

/** 平台数据资源统一分类编码，不等同于国家秘密等级。 */
public enum DataClassification {
    PUBLIC("公开"),
    INTERNAL("内部"),
    SENSITIVE("敏感"),
    RESTRICTED("受限");

    private final String label;

    DataClassification(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public static Optional<DataClassification> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code().equals(code)).findFirst();
    }

    public static boolean supports(String code) {
        return fromCode(code).isPresent();
    }
}

package com.xfwang.platform.api.auth.enums;

import java.util.Arrays;

/** ABAC 策略风险等级。 */
public enum PolicyRiskLevel {
    LOW("低风险"),
    MEDIUM("中风险"),
    HIGH("高风险"),
    CRITICAL("极高风险");

    private final String label;

    PolicyRiskLevel(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public static boolean supports(String code) {
        return code != null && Arrays.stream(values()).anyMatch(value -> value.code().equals(code));
    }
}

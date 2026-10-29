package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** 采集计划、数据源绑定和过滤规则的启停状态。 */
@Getter
@RequiredArgsConstructor
public enum HarvestActivationStatus {
    ACTIVE("ACTIVE", "启用"),
    DISABLED("DISABLED", "停用");

    private final String code;
    private final String label;

    public static boolean supports(String code) {
        return Arrays.stream(values()).anyMatch(value -> value.code.equals(code));
    }
}

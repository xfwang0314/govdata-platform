package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** 采集过滤规则的包含或排除语义。 */
@Getter
@RequiredArgsConstructor
public enum HarvestRuleType {
    INCLUDE("INCLUDE", "包含"),
    EXCLUDE("EXCLUDE", "排除");

    private final String code;
    private final String label;

    public static boolean supports(String code) {
        return Arrays.stream(values()).anyMatch(value -> value.code.equals(code));
    }
}

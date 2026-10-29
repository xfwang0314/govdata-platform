package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/** 采集对象名称匹配方式；正则由线性时间 RE2/J 执行。 */
@Getter
@RequiredArgsConstructor
public enum HarvestMatchType {
    EXACT("EXACT", "精确"),
    PREFIX("PREFIX", "前缀"),
    SUFFIX("SUFFIX", "后缀"),
    CONTAINS("CONTAINS", "包含"),
    REGEX("REGEX", "正则表达式");

    private final String code;
    private final String label;

    public static Optional<HarvestMatchType> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst();
    }
}

package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/** 采集过滤规则作用的元数据层级。 */
@Getter
@RequiredArgsConstructor
public enum HarvestRuleScope {
    CATALOG("CATALOG", "Catalog"),
    SCHEMA("SCHEMA", "Schema"),
    TABLE("TABLE", "表");

    private final String code;
    private final String label;

    public static Optional<HarvestRuleScope> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst();
    }
}

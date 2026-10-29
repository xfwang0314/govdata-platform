package com.xfwang.govdata.metadata.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 元数据资产在源端的技术可用状态。 */
@Getter
@RequiredArgsConstructor
public enum MetadataTechnicalStatus {
    ACTIVE("ACTIVE", "正常"),
    MISSING("MISSING", "源端已缺失"),
    OUT_OF_SCOPE("OUT_OF_SCOPE", "不在当前采集范围"),
    DISABLED("DISABLED", "已停用");

    private final String code;
    private final String label;
}

package com.xfwang.govdata.metadata.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 元数据资产人工治理生命周期。 */
@Getter
@RequiredArgsConstructor
public enum MetadataGovernanceStatus {
    UNMANAGED("UNMANAGED", "未治理"),
    MANAGED("MANAGED", "已治理"),
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;
    private final String label;
}

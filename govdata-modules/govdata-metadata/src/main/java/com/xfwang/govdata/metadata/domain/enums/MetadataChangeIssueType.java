package com.xfwang.govdata.metadata.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 需要人工处置的元数据高风险问题类型。 */
@Getter
@RequiredArgsConstructor
public enum MetadataChangeIssueType {
    STRUCTURE_BREAKING("STRUCTURE_BREAKING", "破坏性结构变化"),
    TABLE_MISSING("TABLE_MISSING", "表已消失");

    private final String code;
    private final String label;
}

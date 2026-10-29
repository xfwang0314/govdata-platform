package com.xfwang.govdata.metadata.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 表技术内容变化等级。 */
@Getter
@RequiredArgsConstructor
public enum MetadataChangeLevel {
    DOCUMENTATION_ONLY("DOCUMENTATION_ONLY", "仅技术文档变化"),
    NON_BREAKING("NON_BREAKING", "非破坏性变化"),
    BREAKING("BREAKING", "破坏性变化");

    private final String code;
    private final String label;
}

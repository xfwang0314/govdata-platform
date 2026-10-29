package com.xfwang.govdata.metadata.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 高风险结构变更问题状态。 */
@Getter
@RequiredArgsConstructor
public enum MetadataChangeIssueStatus {
    OPEN("OPEN", "待确认"),
    ACKNOWLEDGED("ACKNOWLEDGED", "已确认"),
    REMEDIATING("REMEDIATING", "处理中"),
    RESOLVED("RESOLVED", "已解决"),
    DISMISSED("DISMISSED", "已忽略");

    private final String code;
    private final String label;
}

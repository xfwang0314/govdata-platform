package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 采集运行及数据源分项的稳定状态。 */
@Getter
@RequiredArgsConstructor
public enum HarvestRunStatus {
    QUEUED("QUEUED", "排队中"),
    RUNNING("RUNNING", "运行中"),
    SUCCESS("SUCCESS", "成功"),
    ANOMALOUS("ANOMALOUS", "异常待确认"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;
}

package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 采集运行触发来源。 */
@Getter
@RequiredArgsConstructor
public enum HarvestTriggerType {
    MANUAL("MANUAL", "手工"),
    CRON("CRON", "Cron"),
    RETRY("RETRY", "重试");

    private final String code;
    private final String label;
}

package com.xfwang.govdata.metadata.harvest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** 采集计划调度方式。 */
@Getter
@RequiredArgsConstructor
public enum HarvestScheduleType {
    MANUAL("MANUAL", "手工"),
    CRON("CRON", "Cron");

    private final String code;
    private final String label;

    public static boolean supports(String code) {
        return Arrays.stream(values()).anyMatch(value -> value.code.equals(code));
    }
}

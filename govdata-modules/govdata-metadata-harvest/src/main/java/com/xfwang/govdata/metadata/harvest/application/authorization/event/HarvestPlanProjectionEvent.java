package com.xfwang.govdata.metadata.harvest.application.authorization.event;

/** 采集计划向统一 Resource 目录投影的事务后事件。 */
public record HarvestPlanProjectionEvent(Long planId, String planName, Long ownerDeptId,
                                         String scheduleType, String status, Operation operation) {
    public enum Operation { UPSERT, ARCHIVE }
}

package com.xfwang.govdata.metadata.harvest.application.scheduling;

/** 采集计划事务提交后同步外部调度器的领域应用事件。 */
public record HarvestScheduleChangedEvent(
    String tenantId,
    Long harvestPlanId,
    Long creatorUserId,
    String scheduleType,
    String cronExpression,
    String planStatus,
    Operation operation
) {
    public enum Operation {
        UPSERT,
        REMOVE
    }
}

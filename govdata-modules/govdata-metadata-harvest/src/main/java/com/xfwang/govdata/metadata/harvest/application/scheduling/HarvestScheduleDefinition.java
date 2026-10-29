package com.xfwang.govdata.metadata.harvest.application.scheduling;

/** 调度 Adapter 所需的最小不可变计划定义。 */
public record HarvestScheduleDefinition(
    String tenantId,
    Long harvestPlanId,
    Long creatorUserId,
    String cronExpression
) {
}

package com.xfwang.govdata.metadata.harvest.application.scheduling;

/**
 * 采集计划与外部调度实现之间的 seam。
 *
 * <p>调用方不感知 Quartz JobKey、Trigger 或持久化表；替换调度实现只需提供新的 Adapter。</p>
 */
public interface HarvestScheduleRegistrar {

    void replace(HarvestScheduleDefinition definition);

    void remove(String tenantId, Long harvestPlanId);
}

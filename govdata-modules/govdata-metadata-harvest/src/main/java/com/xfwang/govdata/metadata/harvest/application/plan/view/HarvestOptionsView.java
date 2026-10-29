package com.xfwang.govdata.metadata.harvest.application.plan.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 元数据采集页面固定选项集合。 */
@Value
@Builder
public class HarvestOptionsView {
    List<HarvestOptionView> scheduleTypes;
    List<HarvestOptionView> planStatuses;
    List<HarvestOptionView> ruleScopes;
    List<HarvestOptionView> ruleTypes;
    List<HarvestOptionView> matchTypes;
    List<HarvestOptionView> runStatuses;
}

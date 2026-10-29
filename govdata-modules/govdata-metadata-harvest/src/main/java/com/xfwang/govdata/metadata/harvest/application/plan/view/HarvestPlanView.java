package com.xfwang.govdata.metadata.harvest.application.plan.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class HarvestPlanView {
    Long harvestPlanId;
    String harvestPlanCode;
    String harvestPlanName;
    Long datasourceId;
    Long datasourceResourceId;
    String scheduleType;
    String cronExpression;
    Long ownerDeptId;
    String planStatus;
    Integer currentPlanRevisionNo;
    String catalogName;
    String schemaName;
    Integer maxTableCount;
    Boolean rawArchiveFlag;
    Integer lockVersion;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    List<HarvestRuleView> rules;
}

package com.xfwang.govdata.metadata.harvest.application.plan.query;

import lombok.Data;

/** 采集计划查询条件。 */
@Data
public class HarvestPlanQuery {
    private String keyword;
    private Long datasourceId;
    private String scheduleType;
    private String planStatus;
    private Long ownerDeptId;
}

package com.xfwang.govdata.metadata.harvest.infrastructure.scheduling.quartz;

import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestSchedulingService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/** Quartz 协议入口；业务执行语义仍由采集应用模块负责。 */
@DisallowConcurrentExecution
public class QuartzHarvestJob implements Job {
    static final String TENANT_ID = "tenantId";
    static final String PLAN_ID = "harvestPlanId";
    static final String CREATOR_USER_ID = "creatorUserId";

    private HarvestSchedulingService schedulingService;

    /** Quartz 负责实例化 Job，因此依赖由自定义 JobFactory 完成 setter 注入。 */
    @Autowired
    public void setSchedulingService(HarvestSchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        var data = context.getMergedJobDataMap();
        schedulingService.executeScheduled(data.getString(TENANT_ID), data.getLong(PLAN_ID),
            data.getLong(CREATOR_USER_ID), context.getFireInstanceId());
    }
}

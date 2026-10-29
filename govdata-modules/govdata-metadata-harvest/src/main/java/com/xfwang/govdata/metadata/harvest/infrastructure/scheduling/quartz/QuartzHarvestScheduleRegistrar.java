package com.xfwang.govdata.metadata.harvest.infrastructure.scheduling.quartz;

import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestScheduleDefinition;
import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestScheduleRegistrar;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

/** 默认 Quartz 调度 Adapter。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "govdata.metadata.harvest.scheduler.provider", havingValue = "quartz",
    matchIfMissing = true)
public class QuartzHarvestScheduleRegistrar implements HarvestScheduleRegistrar {
    private static final String GROUP = "govdata-metadata-harvest";
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    private final Scheduler scheduler;

    @Override
    public void replace(HarvestScheduleDefinition definition) {
        JobKey jobKey = jobKey(definition.tenantId(), definition.harvestPlanId());
        TriggerKey triggerKey = triggerKey(definition.tenantId(), definition.harvestPlanId());
        var job = JobBuilder.newJob(QuartzHarvestJob.class)
            .withIdentity(jobKey)
            .usingJobData(QuartzHarvestJob.TENANT_ID, definition.tenantId())
            .usingJobData(QuartzHarvestJob.PLAN_ID, definition.harvestPlanId())
            .usingJobData(QuartzHarvestJob.CREATOR_USER_ID, definition.creatorUserId())
            .storeDurably()
            .requestRecovery()
            .build();
        var trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .forJob(jobKey)
            .withSchedule(CronScheduleBuilder.cronSchedule(definition.cronExpression())
                .inTimeZone(ZONE)
                .withMisfireHandlingInstructionDoNothing())
            .build();
        try {
            scheduler.addJob(job, true, true);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException | RuntimeException exception) {
            throw new ServiceException(HarvestErrorCode.SCHEDULER_FAILED);
        }
    }

    @Override
    public void remove(String tenantId, Long harvestPlanId) {
        try {
            scheduler.deleteJob(jobKey(tenantId, harvestPlanId));
        } catch (SchedulerException exception) {
            throw new ServiceException(HarvestErrorCode.SCHEDULER_FAILED);
        }
    }

    private JobKey jobKey(String tenantId, Long planId) {
        return JobKey.jobKey(tenantId + ':' + planId, GROUP);
    }

    private TriggerKey triggerKey(String tenantId, Long planId) {
        return TriggerKey.triggerKey(tenantId + ':' + planId, GROUP);
    }
}

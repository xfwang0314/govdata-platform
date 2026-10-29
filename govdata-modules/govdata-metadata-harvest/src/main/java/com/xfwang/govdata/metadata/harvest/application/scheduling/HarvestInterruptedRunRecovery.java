package com.xfwang.govdata.metadata.harvest.application.scheduling;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRun;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRunStatus;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 单实例内存调度重启时，关闭上次进程遗留的运行占用。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.quartz.job-store-type", havingValue = "memory", matchIfMissing = true)
public class HarvestInterruptedRunRecovery {
    private final HarvestRunMapper runMapper;

    public void recover() {
        LocalDateTime now = LocalDateTime.now();
        int recovered = TenantHelper.ignore(() -> runMapper.update(null, new LambdaUpdateWrapper<HarvestRun>()
            .isNotNull(HarvestRun::getActiveDatasourceKey)
            .in(HarvestRun::getRunStatus, HarvestRunStatus.RUNNING.getCode(), HarvestRunStatus.QUEUED.getCode())
            .lt(HarvestRun::getStartedTime, now)
            .set(HarvestRun::getRunStatus, HarvestRunStatus.FAILED.getCode())
            .set(HarvestRun::getFailureCode, "HARVEST_INTERRUPTED")
            .set(HarvestRun::getFailureSummary, "上次服务中断，运行未完成")
            .set(HarvestRun::getCompletedTime, now)
            .set(HarvestRun::getActiveDatasourceKey, null)));
        if (recovered > 0) {
            log.warn("已恢复上次服务中断的元数据采集运行 count={}", recovered);
        }
    }
}

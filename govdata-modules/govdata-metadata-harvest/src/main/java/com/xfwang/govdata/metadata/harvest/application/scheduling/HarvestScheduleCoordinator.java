package com.xfwang.govdata.metadata.harvest.application.scheduling;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlan;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestScheduleType;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 将业务计划状态收敛为调度 Adapter 的最小注册动作。 */
@Service
@RequiredArgsConstructor
public class HarvestScheduleCoordinator {
    private final HarvestPlanMapper planMapper;
    private final HarvestScheduleRegistrar registrar;

    public void synchronize(HarvestScheduleChangedEvent event) {
        if (event.operation() == HarvestScheduleChangedEvent.Operation.REMOVE || !scheduled(event)) {
            registrar.remove(event.tenantId(), event.harvestPlanId());
            return;
        }
        registrar.replace(new HarvestScheduleDefinition(event.tenantId(), event.harvestPlanId(),
            event.creatorUserId(), event.cronExpression()));
    }

    public void reconcileAll() {
        List<HarvestPlan> plans = TenantHelper.ignore(() -> planMapper.selectList(new LambdaQueryWrapper<HarvestPlan>()
            .eq(HarvestPlan::getPlanStatus, HarvestActivationStatus.ACTIVE.getCode())
            .eq(HarvestPlan::getScheduleType, HarvestScheduleType.CRON.getCode())));
        plans.forEach(plan -> registrar.replace(new HarvestScheduleDefinition(plan.getTenantId(),
            plan.getHarvestPlanId(), plan.getCreateBy(), plan.getCronExpression())));
    }

    private boolean scheduled(HarvestScheduleChangedEvent event) {
        return HarvestActivationStatus.ACTIVE.getCode().equals(event.planStatus())
            && HarvestScheduleType.CRON.getCode().equals(event.scheduleType());
    }
}

package com.xfwang.govdata.metadata.harvest.infrastructure.authorization;

import com.xfwang.govdata.metadata.harvest.application.authorization.event.HarvestPlanProjectionEvent;
import com.xfwang.govdata.metadata.harvest.domain.authorization.HarvestAuthorizationCodes;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlan;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanMapper;
import com.xfwang.platform.api.auth.enums.ResourceStatus;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** 采集计划提交后投影统一资源，失败时业务对象保持 fail-closed。 */
@Component
@RequiredArgsConstructor
public class HarvestPlanProjectionHandler {
    private final ResourceRegistry resourceRegistry;
    private final HarvestPlanMapper planMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void project(HarvestPlanProjectionEvent event) {
        if (event.operation() == HarvestPlanProjectionEvent.Operation.ARCHIVE) {
            resourceRegistry.findByBusinessKey(HarvestAuthorizationCodes.MODULE,
                HarvestAuthorizationCodes.RESOURCE_TYPE, String.valueOf(event.planId()))
                .ifPresent(value -> resourceRegistry.changeStatus(value.getResourceId(), ResourceStatus.ARCHIVED.code()));
            return;
        }
        ResourceDescriptor resource = resourceRegistry.register(ResourceDescriptor.builder()
            .resourceName(event.planName()).resourceType(HarvestAuthorizationCodes.RESOURCE_TYPE)
            .businessId(String.valueOf(event.planId())).sourceModule(HarvestAuthorizationCodes.MODULE)
            .ownerDeptId(event.ownerDeptId())
            .status(HarvestActivationStatus.ACTIVE.getCode().equals(event.status())
                ? ResourceStatus.ACTIVE.code() : ResourceStatus.DISABLED.code())
            .attributes(Map.of("harvestPlan", Map.of("scheduleType", event.scheduleType(), "status", event.status())))
            .build());
        HarvestPlan projection = new HarvestPlan();
        projection.setHarvestPlanId(event.planId());
        projection.setAuthzResourceId(resource.getResourceId());
        planMapper.updateById(projection);
    }
}

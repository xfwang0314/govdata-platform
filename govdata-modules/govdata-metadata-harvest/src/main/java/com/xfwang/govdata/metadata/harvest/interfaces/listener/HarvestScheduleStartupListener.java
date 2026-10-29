package com.xfwang.govdata.metadata.harvest.interfaces.listener;

import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestScheduleCoordinator;
import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestInterruptedRunRecovery;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 应用启动后将控制库计划与调度实现对账。 */
@Component
@RequiredArgsConstructor
public class HarvestScheduleStartupListener {
    private final HarvestScheduleCoordinator coordinator;
    private final ObjectProvider<HarvestInterruptedRunRecovery> interruptedRunRecovery;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        interruptedRunRecovery.ifAvailable(HarvestInterruptedRunRecovery::recover);
        coordinator.reconcileAll();
    }
}

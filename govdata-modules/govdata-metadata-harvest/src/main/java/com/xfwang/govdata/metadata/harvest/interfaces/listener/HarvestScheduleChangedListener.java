package com.xfwang.govdata.metadata.harvest.interfaces.listener;

import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestScheduleChangedEvent;
import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestScheduleCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在计划事务提交后同步外部调度器。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HarvestScheduleChangedListener {
    private final HarvestScheduleCoordinator coordinator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(HarvestScheduleChangedEvent event) {
        synchronizeSafely(event);
    }

    @EventListener
    public void withoutTransaction(HarvestScheduleChangedEvent event) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            synchronizeSafely(event);
        }
    }

    private void synchronizeSafely(HarvestScheduleChangedEvent event) {
        try {
            coordinator.synchronize(event);
        } catch (RuntimeException ex) {
            // 领域计划已经提交，不能把调度器故障伪装成业务事务回滚；启动对账可恢复注册状态。
            log.error("同步元数据采集调度失败 tenantId={} planId={} operation={}",
                event.tenantId(), event.harvestPlanId(), event.operation(), ex);
        }
    }
}

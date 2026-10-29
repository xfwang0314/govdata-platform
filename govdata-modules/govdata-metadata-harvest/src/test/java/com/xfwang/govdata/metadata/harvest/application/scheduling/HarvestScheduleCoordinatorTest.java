package com.xfwang.govdata.metadata.harvest.application.scheduling;

import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestScheduleType;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class HarvestScheduleCoordinatorTest {

    private final HarvestScheduleRegistrar registrar = mock(HarvestScheduleRegistrar.class);
    private final HarvestScheduleCoordinator coordinator = new HarvestScheduleCoordinator(
        mock(HarvestPlanMapper.class), registrar);

    @Test
    void shouldRegisterActiveCronPlanThroughSchedulerSeam() {
        coordinator.synchronize(new HarvestScheduleChangedEvent("000000", 10L, 1L,
            HarvestScheduleType.CRON.getCode(), "0 0 2 * * *", HarvestActivationStatus.ACTIVE.getCode(),
            HarvestScheduleChangedEvent.Operation.UPSERT));

        verify(registrar).replace(new HarvestScheduleDefinition("000000", 10L, 1L, "0 0 2 * * *"));
        verifyNoMoreInteractions(registrar);
    }

    @Test
    void shouldRemoveManualOrDisabledPlan() {
        coordinator.synchronize(new HarvestScheduleChangedEvent("000000", 10L, 1L,
            HarvestScheduleType.MANUAL.getCode(), null, HarvestActivationStatus.ACTIVE.getCode(),
            HarvestScheduleChangedEvent.Operation.UPSERT));

        verify(registrar).remove("000000", 10L);
        verifyNoMoreInteractions(registrar);
    }
}

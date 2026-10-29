package com.xfwang.govdata.metadata.harvest.application.run;

import com.xfwang.govdata.api.datasource.DataSourceReference;
import com.xfwang.govdata.api.datasource.DataSourceReferenceProvider;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryGateway;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryResult;
import com.xfwang.govdata.api.metadata.MetadataObservationWriter;
import com.xfwang.govdata.api.metadata.MetadataRunCompletionResult;
import com.xfwang.govdata.metadata.harvest.application.archive.HarvestRawArchiveService;
import com.xfwang.govdata.metadata.harvest.application.plan.HarvestPlanService;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlan;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlanRevision;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRun;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.service.HarvestRuleEvaluator;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRunFailureMapper;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRunMapper;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HarvestRunServiceTest {

    @Test
    void shouldPersistTenantSnapshotForQuartzRunWithoutHttpContext() {
        HarvestPlanService planService = mock(HarvestPlanService.class);
        HarvestRunMapper runMapper = mock(HarvestRunMapper.class);
        HarvestRunFailureMapper failureMapper = mock(HarvestRunFailureMapper.class);
        MetadataDiscoveryGateway discoveryGateway = mock(MetadataDiscoveryGateway.class);
        MetadataObservationWriter observationWriter = mock(MetadataObservationWriter.class);
        DataSourceReferenceProvider datasourceProvider = mock(DataSourceReferenceProvider.class);
        SubjectProvider subjectProvider = mock(SubjectProvider.class);

        HarvestPlan plan = new HarvestPlan();
        plan.setHarvestPlanId(21L);
        plan.setTenantId("000000");
        plan.setPlanStatus(HarvestActivationStatus.ACTIVE.getCode());
        plan.setDatasourceId(31L);
        plan.setDatasourceResourceId(32L);
        plan.setOwnerDeptId(41L);
        HarvestPlanRevision revision = new HarvestPlanRevision();
        revision.setPlanRevisionId(22L);
        revision.setScopeJson("{}");
        when(planService.requirePlan(21L)).thenReturn(plan);
        when(planService.requireCurrentRevision(plan)).thenReturn(revision);
        when(planService.currentRules(revision)).thenReturn(List.of());
        when(datasourceProvider.findById(31L)).thenReturn(Optional.of(DataSourceReference.builder()
            .datasourceId(31L).resourceId(32L).ownerDeptId(41L)
            .connectorCode("mysql").connectorVersion("1").build()));
        when(subjectProvider.currentSubject()).thenReturn(SubjectContext.builder()
            .tenantId("000000").principalType(PrincipalType.HUMAN).principalId("1").userId(1L).build());
        when(discoveryGateway.discover(any(), any())).thenReturn(MetadataDiscoveryResult.builder()
            .complete(true).connectorCode("mysql").connectorVersion("1").build());
        when(observationWriter.completeRun(any(), any(Boolean.class), any(Boolean.class)))
            .thenReturn(MetadataRunCompletionResult.skipped());

        AtomicReference<HarvestRun> inserted = new AtomicReference<>();
        when(runMapper.insert(any(HarvestRun.class))).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            throw new IllegalStateException("stop after inspecting insert entity");
        });

        HarvestRunService service = new HarvestRunService(planService, runMapper, failureMapper,
            discoveryGateway, observationWriter, datasourceProvider, mock(HarvestRuleEvaluator.class),
            subjectProvider, mock(HarvestRawArchiveService.class));

        assertThatThrownBy(() -> service.executeScheduled(21L, "quartz-fire-1"))
            .isInstanceOf(IllegalStateException.class);

        assertThat(inserted.get().getTenantId()).isEqualTo("000000");
        assertThat(inserted.get().getTriggerType()).isEqualTo("CRON");
        assertThat(inserted.get().getTriggerRef()).isEqualTo("quartz-fire-1");
    }
}

package com.xfwang.govdata.metadata.harvest.application.run;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.api.datasource.DataSourceReference;
import com.xfwang.govdata.api.datasource.DataSourceReferenceProvider;
import com.xfwang.govdata.api.metadata.CollectedMetadataNamespace;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.api.metadata.MetadataBatchWriteResult;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryGateway;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryRequest;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryResult;
import com.xfwang.govdata.api.metadata.MetadataDiscoverySink;
import com.xfwang.govdata.api.metadata.MetadataObservationContext;
import com.xfwang.govdata.api.metadata.MetadataObservationWriter;
import com.xfwang.govdata.api.metadata.MetadataRunCompletionResult;
import com.xfwang.govdata.metadata.harvest.application.archive.HarvestRawArchiveService;
import com.xfwang.govdata.metadata.harvest.application.plan.HarvestPlanService;
import com.xfwang.govdata.metadata.harvest.application.run.query.HarvestRunQuery;
import com.xfwang.govdata.metadata.harvest.application.run.view.HarvestRunFailureView;
import com.xfwang.govdata.metadata.harvest.application.run.view.HarvestRunView;
import com.xfwang.govdata.metadata.harvest.domain.authorization.HarvestAuthorizationCodes;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlan;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlanRevision;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRule;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRun;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRunFailure;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRunStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestTriggerType;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import com.xfwang.govdata.metadata.harvest.domain.service.HarvestRuleEvaluator;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRunFailureMapper;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRunMapper;
import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.dromara.common.mybatis.annotation.ScopeColumn;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单数据源元数据发现、过滤和批量生效编排服务。 */
@Service
@RequiredArgsConstructor
public class HarvestRunService {
    private final HarvestPlanService planService;
    private final HarvestRunMapper runMapper;
    private final HarvestRunFailureMapper failureMapper;
    private final MetadataDiscoveryGateway discoveryGateway;
    private final MetadataObservationWriter observationWriter;
    private final DataSourceReferenceProvider datasourceProvider;
    private final HarvestRuleEvaluator ruleEvaluator;
    private final SubjectProvider subjectProvider;
    private final HarvestRawArchiveService rawArchiveService;

    @ResourceAuthorize(businessId = "#planId", sourceModule = HarvestAuthorizationCodes.MODULE,
        resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE, action = HarvestAuthorizationCodes.ACTION_EXECUTE,
        purpose = HarvestAuthorizationCodes.PURPOSE)
    public HarvestRunView executeManual(Long planId) {
        return execute(planId, HarvestTriggerType.MANUAL.getCode(), null);
    }

    @ResourceAuthorize(businessId = "#planId", sourceModule = HarvestAuthorizationCodes.MODULE,
        resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE, action = HarvestAuthorizationCodes.ACTION_EXECUTE,
        purpose = HarvestAuthorizationCodes.PURPOSE)
    public HarvestRunView executeScheduled(Long planId, String triggerRef) {
        return execute(planId, HarvestTriggerType.CRON.getCode(), triggerRef);
    }

    @ResourceAuthorize(businessId = "@harvestRunResourceResolver.resolvePlanId(#runId)",
        sourceModule = HarvestAuthorizationCodes.MODULE, resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE,
        action = HarvestAuthorizationCodes.ACTION_EXECUTE, purpose = HarvestAuthorizationCodes.PURPOSE)
    public HarvestRunView retry(Long runId) {
        HarvestRun previous = requireRun(runId);
        if (HarvestRunStatus.RUNNING.getCode().equals(previous.getRunStatus())
            || HarvestRunStatus.QUEUED.getCode().equals(previous.getRunStatus())) {
            throw new ServiceException(HarvestErrorCode.RUN_CONFLICT);
        }
        return execute(previous.getHarvestPlanId(), HarvestTriggerType.RETRY.getCode(), previous.getHarvestRunNo());
    }

    private HarvestRunView execute(Long planId, String triggerType, String triggerRef) {
        HarvestPlan plan = planService.requirePlan(planId);
        if (!HarvestActivationStatus.ACTIVE.getCode().equals(plan.getPlanStatus())) {
            throw new ServiceException(HarvestErrorCode.PLAN_DISABLED);
        }
        HarvestPlanRevision revision = planService.requireCurrentRevision(plan);
        List<HarvestRule> rules = planService.currentRules(revision);
        DataSourceReference datasource = datasourceProvider.findById(plan.getDatasourceId())
            .orElseThrow(() -> new ServiceException(HarvestErrorCode.INVALID_PLAN, "数据源不存在"));
        HarvestRun run = startRun(plan, revision, datasource, triggerType, triggerRef);
        RunAccumulator accumulator = new RunAccumulator();
        MetadataObservationContext context = context(run, datasource);
        Map<String, Object> scope = JsonUtils.parseObject(revision.getScopeJson(),
            new TypeReference<Map<String, Object>>() { });
        try {
            MetadataDiscoveryResult result = discoveryGateway.discover(MetadataDiscoveryRequest.builder()
                .harvestRunId(run.getHarvestRunId()).datasourceId(plan.getDatasourceId())
                .catalogName(string(scope, "catalogName")).schemaName(string(scope, "schemaName"))
                .maxTableCount(integer(scope, "maxTableCount", 1000)).build(),
                sink(run, revision, rules, context, accumulator));
            boolean complete = Boolean.TRUE.equals(result.getComplete()) && accumulator.failures == 0;
            MetadataRunCompletionResult completion = observationWriter.completeRun(context, complete,
                isAuthoritativeFullScope(scope, rules));
            boolean anomalous = Boolean.TRUE.equals(completion.getMissingSuppressed());
            finishRun(run.getHarvestRunId(), result, accumulator, completion, complete, anomalous,
                anomalous ? HarvestErrorCode.MASS_MISSING_SUPPRESSED.getCode() : null);
        } catch (Exception exception) {
            String code = errorCode(exception);
            recordFailure(run, "DISCOVERY", null, code);
            finishRun(run.getHarvestRunId(), null, accumulator, MetadataRunCompletionResult.skipped(),
                false, false, code);
        }
        return get(run.getHarvestRunId());
    }

    private MetadataDiscoverySink sink(HarvestRun run, HarvestPlanRevision revision, List<HarvestRule> rules,
                                       MetadataObservationContext context, RunAccumulator accumulator) {
        return new MetadataDiscoverySink() {
            private final Set<String> acceptedNamespaceKeys = new HashSet<>();
            private int archiveBatchNo;

            @Override
            public void acceptNamespaces(List<CollectedMetadataNamespace> namespaces) {
                archive(run, revision, ++archiveBatchNo, namespaces);
                List<CollectedMetadataNamespace> accepted = ruleEvaluator.filterNamespaces(namespaces, rules).stream()
                    .filter(value -> value.getParentCanonicalKey() == null
                        || acceptedNamespaceKeys.contains(value.getParentCanonicalKey()))
                    .toList();
                accepted.forEach(value -> acceptedNamespaceKeys.add(value.getCanonicalKey()));
                if (!accepted.isEmpty()) observationWriter.writeNamespaces(context, accepted);
            }

            @Override
            public void acceptTables(List<CollectedMetadataTable> tables) {
                archive(run, revision, ++archiveBatchNo, tables);
                List<CollectedMetadataTable> accepted = ruleEvaluator.filterTables(tables, rules).stream()
                    .filter(value -> value.getParentCanonicalKey() == null
                        || acceptedNamespaceKeys.contains(value.getParentCanonicalKey()))
                    .toList();
                accumulator.discovered += accepted.size();
                writeTablesResilient(run, context, accepted, accumulator);
            }
        };
    }

    private void writeTablesResilient(HarvestRun run, MetadataObservationContext context,
                                      List<CollectedMetadataTable> tables, RunAccumulator accumulator) {
        if (tables.isEmpty()) return;
        try {
            MetadataBatchWriteResult result = observationWriter.writeTables(context, tables);
            accumulator.changed += result.getChangedTableCount();
        } catch (Exception exception) {
            if (tables.size() > 1) {
                int middle = tables.size() / 2;
                writeTablesResilient(run, context, List.copyOf(tables.subList(0, middle)), accumulator);
                writeTablesResilient(run, context, List.copyOf(tables.subList(middle, tables.size())), accumulator);
                return;
            }
            accumulator.failures++;
            recordFailure(run, "TABLE", tables.get(0).getCanonicalKey(), errorCode(exception));
        }
    }

    private HarvestRun startRun(HarvestPlan plan, HarvestPlanRevision revision, DataSourceReference datasource,
                                String triggerType, String triggerRef) {
        long id = IdWorker.getId();
        SubjectContext subject = subjectProvider.currentSubject();
        HarvestRun run = new HarvestRun();
        run.setHarvestRunId(id);
        run.setTenantId(plan.getTenantId());
        run.setHarvestRunNo("mhr_" + Long.toUnsignedString(id, 36));
        run.setHarvestPlanId(plan.getHarvestPlanId());
        run.setPlanRevisionId(revision.getPlanRevisionId());
        run.setDatasourceId(plan.getDatasourceId());
        run.setDatasourceResourceId(plan.getDatasourceResourceId());
        run.setOwnerDeptId(plan.getOwnerDeptId());
        run.setConnectorCode(datasource.getConnectorCode());
        run.setConnectorVersion(datasource.getConnectorVersion());
        run.setTriggerType(triggerType);
        run.setTriggerRef(triggerRef);
        run.setActorType(subject.getPrincipalType() == null ? "HUMAN" : subject.getPrincipalType().name());
        run.setActorId(subject.getPrincipalId() == null ? String.valueOf(subject.getUserId()) : subject.getPrincipalId());
        run.setRunStatus(HarvestRunStatus.RUNNING.getCode());
        run.setActiveDatasourceKey(String.valueOf(plan.getDatasourceId()));
        run.setCompleteRunFlag(false);
        run.setDiscoveredTableCount(0);
        run.setChangedTableCount(0);
        run.setFailedTableCount(0);
        run.setActiveTableCount(0);
        run.setMissingCandidateCount(0);
        run.setMissingMarkedCount(0);
        run.setMissingSuppressedFlag(false);
        run.setStartedTime(LocalDateTime.now());
        run.setTraceId(MDC.get("traceId"));
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(HarvestErrorCode.RUN_CONFLICT);
        }
        return run;
    }

    private MetadataObservationContext context(HarvestRun run, DataSourceReference datasource) {
        return MetadataObservationContext.builder()
            .tenantId(run.getTenantId())
            .harvestRunId(run.getHarvestRunId())
            .datasourceId(run.getDatasourceId()).datasourceResourceId(run.getDatasourceResourceId())
            .ownerDeptId(run.getOwnerDeptId()).connectorCode(datasource.getConnectorCode())
            .connectorVersion(datasource.getConnectorVersion()).actorType(run.getActorType())
            .actorId(run.getActorId()).traceId(run.getTraceId()).build();
    }

    private void finishRun(Long runId, MetadataDiscoveryResult result, RunAccumulator accumulator,
                           MetadataRunCompletionResult completion, boolean complete, boolean anomalous,
                           String failureCode) {
        runMapper.update(null, new LambdaUpdateWrapper<HarvestRun>()
            .eq(HarvestRun::getHarvestRunId, runId)
            .set(HarvestRun::getRunStatus, anomalous ? HarvestRunStatus.ANOMALOUS.getCode()
                : complete ? HarvestRunStatus.SUCCESS.getCode() : HarvestRunStatus.FAILED.getCode())
            .set(HarvestRun::getCompleteRunFlag, complete)
            .set(HarvestRun::getDiscoveredTableCount, accumulator.discovered)
            .set(HarvestRun::getChangedTableCount, accumulator.changed)
            .set(HarvestRun::getFailedTableCount, accumulator.failures)
            .set(HarvestRun::getActiveTableCount, completion.getActiveAssetCount())
            .set(HarvestRun::getMissingCandidateCount, completion.getMissingCandidateCount())
            .set(HarvestRun::getMissingMarkedCount, completion.getMissingMarkedCount())
            .set(HarvestRun::getMissingSuppressedFlag, completion.getMissingSuppressed())
            .set(result != null, HarvestRun::getConnectorCode, result == null ? null : result.getConnectorCode())
            .set(result != null, HarvestRun::getConnectorVersion, result == null ? null : result.getConnectorVersion())
            .set(HarvestRun::getFailureCode, failureCode)
            .set(HarvestRun::getFailureSummary, anomalous
                ? "候选消失表比例超过安全阈值，系统已保留原技术状态，请核对数据源权限和采集范围后重新执行"
                : complete ? null : "元数据采集未完整成功，请依据失败明细和 traceId 排查")
            .set(HarvestRun::getCompletedTime, LocalDateTime.now())
            .set(HarvestRun::getActiveDatasourceKey, null));
    }

    private void recordFailure(HarvestRun run, String scope, String objectKey, String code) {
        HarvestRunFailure value = new HarvestRunFailure();
        value.setRunFailureId(IdWorker.getId());
        value.setTenantId(run.getTenantId());
        value.setHarvestRunId(run.getHarvestRunId());
        value.setFailureScope(scope);
        value.setObjectKey(objectKey);
        value.setAttemptNo(1);
        value.setFailureCode(code);
        value.setFailureSummary("采集对象处理失败，请依据错误编码和 traceId 排查");
        value.setOccurredTime(LocalDateTime.now());
        value.setActorType(run.getActorType());
        value.setActorId(run.getActorId());
        value.setTraceId(run.getTraceId());
        failureMapper.insert(value);
    }

    private void archive(HarvestRun run, HarvestPlanRevision revision, int batchNo, Object batch) {
        if (Boolean.TRUE.equals(revision.getRawArchiveFlag())) {
            rawArchiveService.archive(run.getHarvestRunId(), batchNo, batch);
        }
    }

    @ResourceAuthorize(businessId = "#planId", sourceModule = HarvestAuthorizationCodes.MODULE,
        resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE, action = HarvestAuthorizationCodes.ACTION_VIEW,
        purpose = HarvestAuthorizationCodes.PURPOSE)
    @AbacDataScope(resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE,
        action = HarvestAuthorizationCodes.ACTION_VIEW, purpose = HarvestAuthorizationCodes.PURPOSE,
        columns = @ScopeColumn(attribute = "ownerDeptId", column = "owner_dept_id"))
    public PageResult<HarvestRunView> page(Long planId, HarvestRunQuery query, PageQuery pageQuery) {
        Page<HarvestRun> page = runMapper.selectPage(pageQuery.build(), new LambdaQueryWrapper<HarvestRun>()
            .eq(HarvestRun::getHarvestPlanId, planId)
            .eq(StringUtils.isNotBlank(query.getRunStatus()), HarvestRun::getRunStatus, query.getRunStatus())
            .orderByDesc(HarvestRun::getStartedTime).orderByDesc(HarvestRun::getHarvestRunId));
        return PageResult.build(page.getRecords().stream().map(value -> toView(value, false)).toList(), page.getTotal());
    }

    @ResourceAuthorize(businessId = "@harvestRunResourceResolver.resolvePlanId(#runId)",
        sourceModule = HarvestAuthorizationCodes.MODULE, resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE,
        action = HarvestAuthorizationCodes.ACTION_VIEW, purpose = HarvestAuthorizationCodes.PURPOSE)
    public HarvestRunView get(Long runId) {
        return toView(requireRun(runId), true);
    }

    private HarvestRun requireRun(Long runId) {
        HarvestRun run = runId == null ? null : runMapper.selectById(runId);
        if (run == null) throw new ServiceException(HarvestErrorCode.RUN_NOT_FOUND);
        return run;
    }

    private HarvestRunView toView(HarvestRun run, boolean detail) {
        List<HarvestRunFailureView> failures = detail ? failureMapper.selectList(
            new LambdaQueryWrapper<HarvestRunFailure>().eq(HarvestRunFailure::getHarvestRunId, run.getHarvestRunId())
                .orderByAsc(HarvestRunFailure::getOccurredTime).orderByAsc(HarvestRunFailure::getRunFailureId))
            .stream().map(value -> HarvestRunFailureView.builder().runFailureId(value.getRunFailureId())
                .failureScope(value.getFailureScope()).objectKey(value.getObjectKey()).attemptNo(value.getAttemptNo())
                .failureCode(value.getFailureCode()).failureSummary(value.getFailureSummary())
                .occurredTime(value.getOccurredTime()).build()).toList() : List.of();
        return HarvestRunView.builder().harvestRunId(run.getHarvestRunId()).harvestRunNo(run.getHarvestRunNo())
            .harvestPlanId(run.getHarvestPlanId()).planRevisionId(run.getPlanRevisionId())
            .datasourceId(run.getDatasourceId()).triggerType(run.getTriggerType()).actorType(run.getActorType())
            .actorId(run.getActorId()).runStatus(run.getRunStatus()).completeRunFlag(run.getCompleteRunFlag())
            .discoveredTableCount(run.getDiscoveredTableCount()).changedTableCount(run.getChangedTableCount())
            .failedTableCount(run.getFailedTableCount()).startedTime(run.getStartedTime())
            .activeTableCount(run.getActiveTableCount()).missingCandidateCount(run.getMissingCandidateCount())
            .missingMarkedCount(run.getMissingMarkedCount())
            .missingSuppressedFlag(run.getMissingSuppressedFlag())
            .completedTime(run.getCompletedTime()).traceId(run.getTraceId()).failureCode(run.getFailureCode())
            .failureSummary(run.getFailureSummary()).failures(failures).build();
    }

    private String errorCode(Exception exception) {
        return exception instanceof ServiceException service && StringUtils.isNotBlank(service.getErrorCode())
            ? service.getErrorCode() : HarvestErrorCode.DISCOVERY_FAILED.getCode();
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean isAuthoritativeFullScope(Map<String, Object> scope, List<HarvestRule> rules) {
        return StringUtils.isBlank(string(scope, "catalogName"))
            && StringUtils.isBlank(string(scope, "schemaName"))
            && (rules == null || rules.isEmpty());
    }

    private static final class RunAccumulator {
        private int discovered;
        private int changed;
        private int failures;
    }
}

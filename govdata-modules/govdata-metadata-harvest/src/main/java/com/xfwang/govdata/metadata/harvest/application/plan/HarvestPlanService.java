package com.xfwang.govdata.metadata.harvest.application.plan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.api.datasource.DataSourceReference;
import com.xfwang.govdata.api.datasource.DataSourceReferenceProvider;
import com.xfwang.govdata.metadata.harvest.application.authorization.event.HarvestPlanProjectionEvent;
import com.xfwang.govdata.metadata.harvest.application.plan.command.HarvestPlanCommand;
import com.xfwang.govdata.metadata.harvest.application.plan.command.HarvestRuleCommand;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import com.xfwang.govdata.metadata.harvest.application.plan.query.HarvestPlanQuery;
import com.xfwang.govdata.metadata.harvest.application.plan.view.HarvestPlanView;
import com.xfwang.govdata.metadata.harvest.application.plan.view.HarvestRuleView;
import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestScheduleChangedEvent;
import com.xfwang.govdata.metadata.harvest.domain.authorization.HarvestAuthorizationCodes;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlan;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlanRevision;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRule;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestMatchType;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRuleScope;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRuleType;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestScheduleType;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanMapper;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanRevisionMapper;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRuleMapper;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单数据源采集计划及不可变配置修订应用服务。 */
@Service
@RequiredArgsConstructor
public class HarvestPlanService {
    private final HarvestPlanMapper planMapper;
    private final HarvestPlanRevisionMapper revisionMapper;
    private final HarvestRuleMapper ruleMapper;
    private final DataSourceReferenceProvider datasourceProvider;
    private final SubjectProvider subjectProvider;
    private final ApplicationEventPublisher eventPublisher;

    @AbacDataScope(resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE,
        action = HarvestAuthorizationCodes.ACTION_VIEW, purpose = HarvestAuthorizationCodes.PURPOSE,
        columns = @ScopeColumn(attribute = "ownerDeptId", column = "owner_dept_id"))
    public PageResult<HarvestPlanView> page(HarvestPlanQuery query, PageQuery pageQuery) {
        Page<HarvestPlan> page = planMapper.selectPage(pageQuery.build(), new LambdaQueryWrapper<HarvestPlan>()
            .like(StringUtils.isNotBlank(query.getKeyword()), HarvestPlan::getHarvestPlanName, query.getKeyword())
            .eq(query.getDatasourceId() != null, HarvestPlan::getDatasourceId, query.getDatasourceId())
            .eq(StringUtils.isNotBlank(query.getScheduleType()), HarvestPlan::getScheduleType, query.getScheduleType())
            .eq(StringUtils.isNotBlank(query.getPlanStatus()), HarvestPlan::getPlanStatus, query.getPlanStatus())
            .eq(query.getOwnerDeptId() != null, HarvestPlan::getOwnerDeptId, query.getOwnerDeptId())
            .orderByDesc(HarvestPlan::getUpdateTime).orderByDesc(HarvestPlan::getHarvestPlanId));
        return PageResult.build(page.getRecords().stream().map(value -> toView(value, false)).toList(), page.getTotal());
    }

    @ResourceAuthorize(businessId = "#planId", sourceModule = HarvestAuthorizationCodes.MODULE,
        resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE, action = HarvestAuthorizationCodes.ACTION_VIEW,
        purpose = HarvestAuthorizationCodes.PURPOSE)
    public HarvestPlanView get(Long planId) {
        return toView(requirePlan(planId), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public HarvestPlanView create(HarvestPlanCommand command) {
        validate(command);
        DataSourceReference datasource = requireDatasource(command.getDatasourceId());
        long id = IdWorker.getId();
        HarvestPlan plan = new HarvestPlan();
        plan.setHarvestPlanId(id);
        plan.setHarvestPlanCode("mhp_" + Long.toUnsignedString(id, 36));
        plan.setLockVersion(0);
        plan.setDelFlag("0");
        plan.setCurrentPlanRevisionNo(1);
        copy(command, datasource, plan);
        try {
            planMapper.insert(plan);
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "采集计划标识冲突，请重试");
        }
        appendRevision(plan, command);
        HarvestPlan saved = planMapper.selectById(id);
        publishProjection(saved, HarvestPlanProjectionEvent.Operation.UPSERT);
        publishSchedule(saved, HarvestScheduleChangedEvent.Operation.UPSERT);
        return toView(saved, true);
    }

    @Transactional(rollbackFor = Exception.class)
    @ResourceAuthorize(businessId = "#planId", sourceModule = HarvestAuthorizationCodes.MODULE,
        resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE, action = HarvestAuthorizationCodes.ACTION_MANAGE,
        purpose = HarvestAuthorizationCodes.PURPOSE)
    public HarvestPlanView update(Long planId, HarvestPlanCommand command) {
        validate(command);
        HarvestPlan current = requirePlan(planId);
        if (command.getLockVersion() == null || !command.getLockVersion().equals(current.getLockVersion())) {
            throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "采集计划已被其他用户修改，请刷新后重试");
        }
        DataSourceReference datasource = requireDatasource(command.getDatasourceId());
        HarvestPlan update = new HarvestPlan();
        update.setHarvestPlanId(planId);
        update.setLockVersion(command.getLockVersion());
        update.setCurrentPlanRevisionNo(current.getCurrentPlanRevisionNo() + 1);
        copy(command, datasource, update);
        try {
            if (planMapper.updateById(update) != 1) {
                throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "采集计划版本冲突");
            }
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "采集计划标识冲突，请重试");
        }
        HarvestPlan saved = planMapper.selectById(planId);
        appendRevision(saved, command);
        publishProjection(saved, HarvestPlanProjectionEvent.Operation.UPSERT);
        publishSchedule(saved, HarvestScheduleChangedEvent.Operation.UPSERT);
        return toView(saved, true);
    }

    @Transactional(rollbackFor = Exception.class)
    @ResourceAuthorize(businessId = "#planId", sourceModule = HarvestAuthorizationCodes.MODULE,
        resourceType = HarvestAuthorizationCodes.RESOURCE_TYPE, action = HarvestAuthorizationCodes.ACTION_MANAGE,
        purpose = HarvestAuthorizationCodes.PURPOSE)
    public void remove(Long planId) {
        HarvestPlan plan = requirePlan(planId);
        HarvestPlan release = new HarvestPlan();
        release.setHarvestPlanId(planId);
        release.setLockVersion(plan.getLockVersion());
        release.setPlanStatus(HarvestActivationStatus.DISABLED.getCode());
        if (planMapper.updateById(release) != 1) {
            throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "采集计划版本冲突");
        }
        planMapper.deleteById(planId);
        publishProjection(plan, HarvestPlanProjectionEvent.Operation.ARCHIVE);
        publishSchedule(plan, HarvestScheduleChangedEvent.Operation.REMOVE);
    }

    public HarvestPlan requirePlan(Long id) {
        HarvestPlan value = id == null ? null : planMapper.selectById(id);
        if (value == null) throw new ServiceException(HarvestErrorCode.PLAN_NOT_FOUND);
        return value;
    }

    public HarvestPlanRevision requireCurrentRevision(HarvestPlan plan) {
        HarvestPlanRevision value = revisionMapper.selectOne(new LambdaQueryWrapper<HarvestPlanRevision>()
            .eq(HarvestPlanRevision::getHarvestPlanId, plan.getHarvestPlanId())
            .eq(HarvestPlanRevision::getPlanRevisionNo, plan.getCurrentPlanRevisionNo()));
        if (value == null) throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "采集计划配置修订不存在");
        return value;
    }

    public List<HarvestRule> currentRules(HarvestPlanRevision revision) {
        return ruleMapper.selectList(new LambdaQueryWrapper<HarvestRule>()
            .eq(HarvestRule::getPlanRevisionId, revision.getPlanRevisionId())
            .orderByAsc(HarvestRule::getSortNo).orderByAsc(HarvestRule::getHarvestRuleId));
    }

    private void appendRevision(HarvestPlan plan, HarvestPlanCommand command) {
        SubjectContext subject = subjectProvider.currentSubject();
        long revisionId = IdWorker.getId();
        HarvestPlanRevision revision = new HarvestPlanRevision();
        revision.setPlanRevisionId(revisionId);
        revision.setHarvestPlanId(plan.getHarvestPlanId());
        revision.setPlanRevisionNo(plan.getCurrentPlanRevisionNo());
        revision.setDatasourceId(plan.getDatasourceId());
        revision.setScheduleType(plan.getScheduleType());
        revision.setCronExpression(plan.getCronExpression());
        revision.setScopeJson(plan.getScopeJson());
        revision.setRawArchiveFlag(plan.getRawArchiveFlag());
        revision.setConfigHash(configHash(plan, command.getRules()));
        revision.setActorType(subject.getPrincipalType() == null ? "HUMAN" : subject.getPrincipalType().name());
        revision.setActorId(subject.getPrincipalId() == null ? String.valueOf(subject.getUserId()) : subject.getPrincipalId());
        revision.setTraceId(MDC.get("traceId"));
        revisionMapper.insert(revision);
        List<HarvestRule> rules = new ArrayList<>();
        for (HarvestRuleCommand source : command.getRules()) {
            HarvestRule rule = new HarvestRule();
            rule.setHarvestRuleId(IdWorker.getId());
            rule.setPlanRevisionId(revisionId);
            rule.setRuleScope(source.getRuleScope());
            rule.setRuleType(source.getRuleType());
            rule.setMatchType(source.getMatchType());
            rule.setRulePattern(source.getRulePattern().trim());
            rule.setSortNo(source.getSortNo());
            rules.add(rule);
        }
        if (!rules.isEmpty()) ruleMapper.insertBatch(rules, 500);
    }

    private void validate(HarvestPlanCommand command) {
        if (!HarvestScheduleType.supports(command.getScheduleType())
            || !HarvestActivationStatus.supports(command.getPlanStatus())) invalid();
        if (HarvestScheduleType.MANUAL.getCode().equals(command.getScheduleType())
            && StringUtils.isNotBlank(command.getCronExpression())) invalid();
        if (HarvestScheduleType.CRON.getCode().equals(command.getScheduleType())
            && (StringUtils.isBlank(command.getCronExpression())
            || !CronExpression.isValidExpression(command.getCronExpression()))) invalid();
        Set<String> uniqueRules = new HashSet<>();
        for (HarvestRuleCommand rule : command.getRules()) {
            if (HarvestRuleScope.fromCode(rule.getRuleScope()).isEmpty()
                || !HarvestRuleType.supports(rule.getRuleType())
                || HarvestMatchType.fromCode(rule.getMatchType()).isEmpty()
                || StringUtils.isBlank(rule.getRulePattern())
                || rule.getSortNo() == null || rule.getSortNo() < 0
                || !uniqueRules.add(rule.getRuleScope() + '|' + rule.getRuleType() + '|'
                    + rule.getMatchType() + '|' + rule.getRulePattern())) invalid();
            if (HarvestMatchType.REGEX.getCode().equals(rule.getMatchType())) {
                try {
                    Pattern.compile(rule.getRulePattern());
                } catch (PatternSyntaxException exception) {
                    throw new ServiceException(HarvestErrorCode.INVALID_PLAN,
                        "采集范围正则表达式无效或使用了不支持的语法");
                }
            }
        }
    }

    private DataSourceReference requireDatasource(Long datasourceId) {
        DataSourceReference value = datasourceProvider.findById(datasourceId)
            .orElseThrow(() -> new ServiceException(HarvestErrorCode.INVALID_PLAN, "数据源不存在"));
        if (value.getResourceId() == null || !"ACTIVE".equals(value.getStatus())) {
            throw new ServiceException(HarvestErrorCode.INVALID_PLAN, "数据源必须已启用并完成资源投影");
        }
        return value;
    }

    private void copy(HarvestPlanCommand command, DataSourceReference datasource, HarvestPlan plan) {
        plan.setHarvestPlanName(command.getHarvestPlanName().trim());
        plan.setDatasourceId(datasource.getDatasourceId());
        plan.setDatasourceResourceId(datasource.getResourceId());
        plan.setScheduleType(command.getScheduleType());
        plan.setCronExpression(HarvestScheduleType.CRON.getCode().equals(command.getScheduleType())
            ? command.getCronExpression().trim() : null);
        plan.setOwnerDeptId(command.getOwnerDeptId());
        plan.setPlanStatus(command.getPlanStatus());
        plan.setScopeJson(scopeJson(command));
        plan.setRawArchiveFlag(Boolean.TRUE.equals(command.getRawArchiveFlag()));
    }

    private String scopeJson(HarvestPlanCommand command) {
        Map<String, Object> scope = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(command.getCatalogName())) scope.put("catalogName", command.getCatalogName().trim());
        if (StringUtils.isNotBlank(command.getSchemaName())) scope.put("schemaName", command.getSchemaName().trim());
        scope.put("maxTableCount", command.getMaxTableCount());
        return JsonUtils.toJsonString(scope);
    }

    private String configHash(HarvestPlan plan, List<HarvestRuleCommand> rules) {
        String source = plan.getDatasourceId() + "|" + plan.getScheduleType() + "|" + plan.getCronExpression()
            + "|" + plan.getScopeJson() + "|" + plan.getRawArchiveFlag() + "|" + JsonUtils.toJsonString(rules);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256", exception);
        }
    }

    private HarvestPlanView toView(HarvestPlan plan, boolean detail) {
        Map<String, Object> scope = JsonUtils.parseObject(plan.getScopeJson(), new TypeReference<Map<String, Object>>() { });
        List<HarvestRuleView> rules = List.of();
        if (detail) {
            HarvestPlanRevision revision = requireCurrentRevision(plan);
            rules = currentRules(revision).stream().map(value -> HarvestRuleView.builder()
                .harvestRuleId(value.getHarvestRuleId()).ruleScope(value.getRuleScope())
                .ruleType(value.getRuleType()).matchType(value.getMatchType())
                .rulePattern(value.getRulePattern()).sortNo(value.getSortNo()).build()).toList();
        }
        return HarvestPlanView.builder().harvestPlanId(plan.getHarvestPlanId())
            .harvestPlanCode(plan.getHarvestPlanCode()).harvestPlanName(plan.getHarvestPlanName())
            .datasourceId(plan.getDatasourceId()).datasourceResourceId(plan.getDatasourceResourceId())
            .scheduleType(plan.getScheduleType()).cronExpression(plan.getCronExpression())
            .ownerDeptId(plan.getOwnerDeptId()).planStatus(plan.getPlanStatus())
            .currentPlanRevisionNo(plan.getCurrentPlanRevisionNo())
            .catalogName(string(scope, "catalogName")).schemaName(string(scope, "schemaName"))
            .maxTableCount(integer(scope, "maxTableCount", 1000)).rawArchiveFlag(plan.getRawArchiveFlag())
            .lockVersion(plan.getLockVersion()).createTime(plan.getCreateTime()).updateTime(plan.getUpdateTime())
            .rules(rules).build();
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private void invalid() {
        throw new ServiceException(HarvestErrorCode.INVALID_PLAN);
    }

    private void publishProjection(HarvestPlan plan, HarvestPlanProjectionEvent.Operation operation) {
        eventPublisher.publishEvent(new HarvestPlanProjectionEvent(plan.getHarvestPlanId(), plan.getHarvestPlanName(),
            plan.getOwnerDeptId(), plan.getScheduleType(), plan.getPlanStatus(), operation));
    }

    private void publishSchedule(HarvestPlan plan, HarvestScheduleChangedEvent.Operation operation) {
        eventPublisher.publishEvent(new HarvestScheduleChangedEvent(plan.getTenantId(), plan.getHarvestPlanId(),
            plan.getCreateBy(), plan.getScheduleType(), plan.getCronExpression(), plan.getPlanStatus(), operation));
    }
}

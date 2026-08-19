package com.xfwang.platform.auth.application.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xfwang.platform.api.auth.enums.PolicyAggregateStatus;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.enums.PolicyOperationType;
import com.xfwang.platform.api.auth.enums.PolicyRiskLevel;
import com.xfwang.platform.api.auth.enums.PolicyVersionStatus;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftUpdateCommand;
import com.xfwang.platform.auth.application.policy.query.PolicyGroupQuery;
import com.xfwang.platform.auth.application.policy.view.PolicyGroupView;
import com.xfwang.platform.auth.application.policy.view.PolicyOperationLogView;
import com.xfwang.platform.auth.application.policy.view.PolicyVersionView;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyGroup;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyOperationLog;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeResource;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeRule;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeSubject;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyVersion;
import com.xfwang.platform.auth.domain.policy.model.PolicyDocument;
import com.xfwang.platform.auth.domain.enums.PolicyLifecycleEvent;
import com.xfwang.platform.auth.domain.service.PolicyLifecycle;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyGroupMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyOperationLogMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeResourceMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyVersionMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 策略组是唯一管理、版本和发布聚合根。 */
@Service
@RequiredArgsConstructor
public class PolicyGroupApplicationService {

    private final AuthzPolicyGroupMapper groupMapper;
    private final AuthzPolicyVersionMapper versionMapper;
    private final AuthzPolicyRuntimeRuleMapper runtimeRuleMapper;
    private final AuthzPolicyRuntimeSubjectMapper runtimeSubjectMapper;
    private final AuthzPolicyRuntimeResourceMapper runtimeResourceMapper;
    private final AuthzPolicyOperationLogMapper operationLogMapper;
    private final PolicyDocumentCodec documentCodec;
    private final AuthorizationPolicySchemaValidator schemaValidator;
    private final AuthorizationJsonCodec jsonCodec;
    private final SubjectProvider subjectProvider;
    private final PolicyLifecycle lifecycle;

    public PageResult<PolicyGroupView> page(PolicyGroupQuery query, PageQuery pageQuery) {
        PolicyGroupQuery filter = query == null ? new PolicyGroupQuery() : query;
        List<PolicyGroupView> matched = groupMapper.selectList(new LambdaQueryWrapper<AuthzPolicyGroup>()
                .eq(AuthzPolicyGroup::getDelFlag, Boolean.TRUE.equals(filter.getDeleted()) ? "1" : "0")
                .eq(StringUtils.isNotBlank(filter.getStatus()), AuthzPolicyGroup::getStatus,
                    normalize(filter.getStatus()))
                .orderByDesc(AuthzPolicyGroup::getUpdateTime)
                .orderByDesc(AuthzPolicyGroup::getPolicyGroupId))
            .stream().map(this::toView)
            .filter(view -> StringUtils.isBlank(filter.getKeyword())
                || StringUtils.containsIgnoreCase(view.getPolicyName(), filter.getKeyword())
                || StringUtils.containsIgnoreCase(view.getPolicyGroupCode(), filter.getKeyword()))
            .filter(view -> StringUtils.isBlank(filter.getRiskLevel())
                || normalize(filter.getRiskLevel()).equals(view.getRiskLevel()))
            .toList();
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : Math.max(1, pageQuery.getPageNum());
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null
            ? Integer.MAX_VALUE : Math.max(1, pageQuery.getPageSize());
        long fromLong = (long) (pageNum - 1) * pageSize;
        int from = fromLong >= matched.size() ? matched.size() : (int) fromLong;
        int to = (int) Math.min((long) matched.size(), fromLong + pageSize);
        return PageResult.build(matched.subList(from, to), (long) matched.size());
    }

    public PolicyGroupView get(String groupCode) {
        return toView(requireGroup(groupCode, true));
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView create(PolicyGroupDraftCommand command) {
        validate(command);
        SubjectContext actor = actor();
        PolicyDocument document = documentCodec.from(command);
        String documentJson = documentCodec.write(document);
        long groupId = IdWorker.getId();
        long versionId = IdWorker.getId();
        AuthzPolicyGroup group = new AuthzPolicyGroup();
        group.setPolicyGroupId(groupId);
        group.setPolicyGroupCode("pgrp_" + Long.toUnsignedString(groupId, 36));
        group.setStatus(PolicyAggregateStatus.DRAFT.code());
        group.setDraftVersionId(versionId);
        group.setNextVersionNo(2);
        group.setCreationSource("CUSTOM");
        group.setSourceTemplateCode(null);
        group.setLockVersion(0);
        group.setDelFlag("0");
        group.setCreateBy(actor.getUserId());
        group.setCreateDept(actor.getDeptId());
        group.setUpdateBy(actor.getUserId());
        groupMapper.insert(group);

        AuthzPolicyVersion version = newVersion(groupId, versionId, 1, document, documentJson, actor);
        versionMapper.insert(version);
        record(group, version, PolicyOperationType.CREATE_DRAFT, null, group.getStatus(), null,
            null, version.getContentHash(), actor);
        return toView(group, null, version);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView updateDraft(String groupCode, PolicyGroupDraftUpdateCommand command) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        lifecycle.transitionAggregate(group.getStatus(), PolicyLifecycleEvent.EDIT_DRAFT);
        validate(command);
        AuthzPolicyVersion draft = requireDraft(group);
        if (!Objects.equals(command.getVersionLockVersion(), draft.getLockVersion())) {
            throw validation("待发布版本已被其他操作修改，请刷新后重试");
        }
        PolicyDocument document = documentCodec.from(command);
        String documentJson = documentCodec.write(document);
        String beforeHash = draft.getContentHash();
        applyDocument(draft, document, documentJson);
        if (versionMapper.updateById(draft) != 1) {
            throw validation("待发布版本已被其他操作修改，请刷新后重试");
        }
        SubjectContext actor = actor();
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, draft, PolicyOperationType.UPDATE_DRAFT, group.getStatus(), group.getStatus(), null,
            beforeHash, draft.getContentHash(), actor);
        return toView(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView createNextVersion(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        lifecycle.transitionAggregate(group.getStatus(), PolicyLifecycleEvent.CREATE_VERSION);
        if (group.getCurrentVersionId() == null) {
            throw validation("首次待发布版本无需重复创建新版本");
        }
        if (group.getDraftVersionId() != null) {
            throw validation("策略组已存在 V" + requireDraft(group).getVersionNo()
                + " 待发布版本，请编辑、发布或放弃修改后再创建新版本");
        }
        SubjectContext actor = actor();
        AuthzPolicyVersion current = requireVersion(group.getCurrentVersionId());
        int versionNo = group.getNextVersionNo();
        AuthzPolicyVersion draft = newVersion(group.getPolicyGroupId(), IdWorker.getId(), versionNo,
            documentCodec.read(current.getPolicyDocumentJson()), current.getPolicyDocumentJson(), actor);
        versionMapper.insert(draft);
        group.setDraftVersionId(draft.getPolicyGroupVersionId());
        group.setNextVersionNo(versionNo + 1);
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, draft, PolicyOperationType.CREATE_VERSION, group.getStatus(), group.getStatus(), null,
            current.getContentHash(), draft.getContentHash(), actor);
        return toView(group, current, draft);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView createDraftFromVersion(String groupCode, Long sourceVersionId) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        lifecycle.transitionAggregate(group.getStatus(), PolicyLifecycleEvent.CREATE_VERSION);
        if (group.getDraftVersionId() != null) {
            throw validation("策略组已存在待发布版本");
        }
        AuthzPolicyVersion source = requireVersion(sourceVersionId);
        if (!Objects.equals(source.getPolicyGroupId(), group.getPolicyGroupId())) {
            throw validation("历史版本不属于该策略组");
        }
        SubjectContext actor = actor();
        int versionNo = group.getNextVersionNo();
        AuthzPolicyVersion draft = newVersion(group.getPolicyGroupId(), IdWorker.getId(), versionNo,
            documentCodec.read(source.getPolicyDocumentJson()), source.getPolicyDocumentJson(), actor);
        versionMapper.insert(draft);
        group.setDraftVersionId(draft.getPolicyGroupVersionId());
        group.setNextVersionNo(versionNo + 1);
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, draft, PolicyOperationType.ROLLBACK_DRAFT, group.getStatus(), group.getStatus(), null,
            source.getContentHash(), draft.getContentHash(), actor);
        return toView(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView publish(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        String nextStatus = lifecycle.transitionAggregate(group.getStatus(), PolicyLifecycleEvent.PUBLISH);
        AuthzPolicyVersion draft = requireDraft(group);
        PolicyDocument document = documentCodec.read(draft.getPolicyDocumentJson());
        validate(toCommand(document, group));
        SubjectContext actor = actor();
        AuthzPolicyVersion current = hasSharedVersionPointer(group) || group.getCurrentVersionId() == null
            ? null : requireVersion(group.getCurrentVersionId());
        if (current != null) {
            current.setVersionStatus(lifecycle.transitionVersion(
                current.getVersionStatus(), PolicyLifecycleEvent.REPLACE_CURRENT));
            requireUpdated(versionMapper.updateById(current), "当前策略版本已变化，请刷新后重试");
        }
        draft.setVersionStatus(lifecycle.transitionVersion(
            draft.getVersionStatus(), PolicyLifecycleEvent.PUBLISH));
        draft.setPublishedBy(actor.getUserId());
        draft.setPublishedTime(LocalDateTime.now());
        requireUpdated(versionMapper.updateById(draft), "待发布版本已变化，请刷新后重试");
        replaceRuntime(group, draft, document, actor);
        String before = group.getStatus();
        group.setCurrentVersionId(draft.getPolicyGroupVersionId());
        group.setDraftVersionId(null);
        group.setStatus(nextStatus);
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, draft, PolicyOperationType.PUBLISH, before, group.getStatus(), null,
            current == null ? null : current.getContentHash(), draft.getContentHash(), actor);
        return toView(group, draft, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView disable(String groupCode, String reason) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        String nextStatus = lifecycle.transitionAggregate(group.getStatus(), PolicyLifecycleEvent.DISABLE);
        SubjectContext actor = actor();
        clearRuntime(group.getPolicyGroupId());
        group.setStatus(nextStatus);
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, requireVersion(group.getCurrentVersionId()), PolicyOperationType.DISABLE,
            PolicyAggregateStatus.ACTIVE.code(), group.getStatus(), reason, null, null, actor);
        return toView(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView enable(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        if (group.getDraftVersionId() != null) {
            throw validation("存在待发布版本，请先发布或放弃修改后再重新启用");
        }
        String nextStatus = lifecycle.transitionAggregate(group.getStatus(), PolicyLifecycleEvent.ENABLE);
        SubjectContext actor = actor();
        AuthzPolicyVersion current = requireVersion(group.getCurrentVersionId());
        replaceRuntime(group, current, documentCodec.read(current.getPolicyDocumentJson()), actor);
        group.setStatus(nextStatus);
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, current, PolicyOperationType.ENABLE, PolicyAggregateStatus.DISABLED.code(),
            group.getStatus(), null, null, null, actor);
        return toView(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView discardDraft(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        AuthzPolicyVersion draft = requireDraft(group);
        SubjectContext actor = actor();
        group.setUpdateBy(actor.getUserId());
        if (group.getCurrentVersionId() == null || hasSharedVersionPointer(group)) {
            group.setCurrentVersionId(null);
            group.setStatus(PolicyAggregateStatus.DRAFT.code());
            group.setDelFlag("1");
            group.setDeletedBy(actor.getUserId());
            group.setDeletedTime(LocalDateTime.now());
            group.setDeleteReason("删除从未发布的策略");
        } else {
            draft.setVersionStatus(lifecycle.transitionVersion(
                draft.getVersionStatus(), PolicyLifecycleEvent.DISCARD_PENDING));
            requireUpdated(versionMapper.updateById(draft), "待发布版本已变化，请刷新后重试");
            group.setDraftVersionId(null);
        }
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, draft, PolicyOperationType.DISCARD_DRAFT, group.getStatus(), group.getStatus(),
            group.getDeleteReason(), draft.getContentHash(), null, actor);
        return toView(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String groupCode, String reason) {
        AuthzPolicyGroup group = requireGroup(groupCode, false);
        if (PolicyAggregateStatus.ACTIVE.code().equals(group.getStatus())) {
            throw validation("已启用策略必须先停用后删除");
        }
        if (group.getDraftVersionId() != null) {
            throw validation("存在待发布版本，请先放弃修改后再删除策略组");
        }
        SubjectContext actor = actor();
        clearRuntime(group.getPolicyGroupId());
        group.setDelFlag("1");
        group.setDeletedBy(actor.getUserId());
        group.setDeletedTime(LocalDateTime.now());
        group.setDeleteReason(reason);
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, displayedVersion(group), PolicyOperationType.DELETE, group.getStatus(), group.getStatus(),
            reason, null, null, actor);
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyGroupView restore(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, true);
        if (!"1".equals(group.getDelFlag())) {
            throw validation("策略未被删除");
        }
        SubjectContext actor = actor();
        group.setDelFlag("0");
        group.setDeletedBy(null);
        group.setDeletedTime(null);
        group.setDeleteReason(null);
        group.setStatus(group.getCurrentVersionId() == null
            ? PolicyAggregateStatus.DRAFT.code() : PolicyAggregateStatus.DISABLED.code());
        group.setUpdateBy(actor.getUserId());
        requireUpdated(groupMapper.updateById(group), "策略组状态已变化，请刷新后重试");
        record(group, displayedVersion(group), PolicyOperationType.RESTORE, null, group.getStatus(),
            null, null, null, actor);
        return toView(group);
    }

    public List<PolicyVersionView> versions(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, true);
        return versionMapper.selectList(new LambdaQueryWrapper<AuthzPolicyVersion>()
                .eq(AuthzPolicyVersion::getPolicyGroupId, group.getPolicyGroupId())
                .orderByDesc(AuthzPolicyVersion::getVersionNo))
            .stream().map(this::toVersionView).toList();
    }

    public List<PolicyOperationLogView> operations(String groupCode) {
        AuthzPolicyGroup group = requireGroup(groupCode, true);
        return operationLogMapper.selectList(new LambdaQueryWrapper<AuthzPolicyOperationLog>()
                .eq(AuthzPolicyOperationLog::getPolicyGroupId, group.getPolicyGroupId())
                .orderByDesc(AuthzPolicyOperationLog::getOccurredTime))
            .stream().map(this::toOperationView).toList();
    }

    private void replaceRuntime(AuthzPolicyGroup group, AuthzPolicyVersion version,
                                PolicyDocument document, SubjectContext actor) {
        clearRuntime(group.getPolicyGroupId());
        for (PolicyDocument.SubjectSelector selector : document.subjects().items()) {
            AuthzPolicyRuntimeSubject runtime = new AuthzPolicyRuntimeSubject();
            runtime.setRuntimeSubjectId(IdWorker.getId());
            runtime.setTenantId(actor.getTenantId());
            runtime.setPolicyGroupId(group.getPolicyGroupId());
            runtime.setPolicyGroupVersionId(version.getPolicyGroupVersionId());
            runtime.setSubjectType(selector.subjectType());
            runtime.setSubjectId(selector.subjectId());
            runtime.setIncludeChildren(selector.includeChildren());
            runtime.setCreateBy(actor.getUserId());
            runtime.setCreateTime(LocalDateTime.now());
            runtimeSubjectMapper.insert(runtime);
        }
        for (PolicyDocument.Rule rule : document.rules()) {
            AuthzPolicyRuntimeRule runtime = new AuthzPolicyRuntimeRule();
            runtime.setRuntimeRuleId(IdWorker.getId());
            runtime.setTenantId(actor.getTenantId());
            runtime.setPolicyGroupId(group.getPolicyGroupId());
            runtime.setPolicyGroupVersionId(version.getPolicyGroupVersionId());
            runtime.setRuleCode(rule.ruleCode());
            runtime.setEffect(document.effect());
            runtime.setPriority(document.priority());
            runtime.setResourceType(rule.resourceType());
            runtime.setResourceScope(rule.resourceScope());
            runtime.setActionCodesJson(jsonCodec.write(rule.actions()));
            runtime.setPurposeCodesJson(jsonCodec.write(rule.purposes()));
            runtime.setConditionJson(jsonCodec.write(rule.condition()));
            runtime.setObligationJson(rule.obligation() == null ? null : jsonCodec.write(rule.obligation()));
            runtime.setCreateBy(actor.getUserId());
            runtime.setCreateTime(LocalDateTime.now());
            runtimeRuleMapper.insert(runtime);
            for (PolicyDocument.ResourceTarget target : rule.targets()) {
                AuthzPolicyRuntimeResource resource = new AuthzPolicyRuntimeResource();
                resource.setRuntimeResourceId(IdWorker.getId());
                resource.setTenantId(actor.getTenantId());
                resource.setRuntimeRuleId(runtime.getRuntimeRuleId());
                resource.setResourceId(target.resourceId());
                resource.setIncludeChildren(target.includeChildren());
                resource.setCreateBy(actor.getUserId());
                resource.setCreateTime(LocalDateTime.now());
                runtimeResourceMapper.insert(resource);
            }
        }
    }

    private void clearRuntime(Long groupId) {
        List<Long> ruleIds = runtimeRuleMapper.selectList(new LambdaQueryWrapper<AuthzPolicyRuntimeRule>()
                .eq(AuthzPolicyRuntimeRule::getPolicyGroupId, groupId))
            .stream().map(AuthzPolicyRuntimeRule::getRuntimeRuleId).toList();
        if (!ruleIds.isEmpty()) {
            runtimeResourceMapper.delete(new LambdaQueryWrapper<AuthzPolicyRuntimeResource>()
                .in(AuthzPolicyRuntimeResource::getRuntimeRuleId, ruleIds));
        }
        runtimeRuleMapper.delete(new LambdaQueryWrapper<AuthzPolicyRuntimeRule>()
            .eq(AuthzPolicyRuntimeRule::getPolicyGroupId, groupId));
        runtimeSubjectMapper.delete(new LambdaQueryWrapper<AuthzPolicyRuntimeSubject>()
            .eq(AuthzPolicyRuntimeSubject::getPolicyGroupId, groupId));
    }

    private AuthzPolicyVersion newVersion(Long groupId, Long versionId, int versionNo,
                                          PolicyDocument document, String documentJson, SubjectContext actor) {
        AuthzPolicyVersion version = new AuthzPolicyVersion();
        version.setPolicyGroupVersionId(versionId);
        version.setTenantId(actor.getTenantId());
        version.setPolicyGroupId(groupId);
        version.setVersionNo(versionNo);
        version.setVersionStatus(PolicyVersionStatus.DRAFT.code());
        version.setCreatedBy(actor.getUserId());
        version.setCreatedTime(LocalDateTime.now());
        version.setLockVersion(0);
        applyDocument(version, document, documentJson);
        return version;
    }

    private void applyDocument(AuthzPolicyVersion version, PolicyDocument document, String documentJson) {
        version.setSchemaVersion(document.schemaVersion());
        version.setPolicyNameSnapshot(document.policyName());
        version.setDescriptionSnapshot(document.description());
        version.setRiskLevel(document.riskLevel());
        version.setEffect(document.effect());
        version.setPriority(document.priority());
        version.setPolicyDocumentJson(documentJson);
        version.setCatalogSnapshotJson(jsonCodec.write(document.catalogSnapshots()));
        version.setContentHash(documentCodec.hash(documentJson));
    }

    private void validate(PolicyGroupDraftCommand command) {
        try {
            DecisionType.valueOf(normalize(command.getEffect()));
        } catch (RuntimeException exception) {
            throw validation("策略效果无效");
        }
        if (!PolicyRiskLevel.supports(normalize(command.getRiskLevel()))) {
            throw validation("风险等级无效");
        }
        schemaValidator.validate(command);
    }

    private PolicyGroupDraftCommand toCommand(PolicyDocument document, AuthzPolicyGroup group) {
        PolicyGroupDraftCommand command = new PolicyGroupDraftCommand();
        command.setPolicyName(document.policyName());
        command.setDescription(document.description());
        command.setEffect(document.effect());
        command.setRiskLevel(document.riskLevel());
        command.setPriority(document.priority());
        command.setSubjectOperator(document.subjects().operator());
        command.setSubjects(document.subjects().items().stream().map(selector -> {
            var subject = new com.xfwang.platform.auth.application.policy.command.PolicySubjectCommand();
            subject.setSubjectType(selector.subjectType());
            subject.setSubjectId(selector.subjectId());
            subject.setIncludeChildren(selector.includeChildren());
            return subject;
        }).toList());
        command.setRules(document.rules().stream().map(rule -> {
            var value = new com.xfwang.platform.auth.application.policy.command.PolicyRuleCommand();
            value.setRuleCode(rule.ruleCode());
            value.setResourceType(rule.resourceType());
            value.setResourceScope(rule.resourceScope());
            value.setActions(new LinkedHashSet<>(rule.actions()));
            value.setPurposes(new LinkedHashSet<>(rule.purposes()));
            value.setConditionJson(jsonCodec.write(rule.condition()));
            value.setObligationJson(rule.obligation() == null ? null : jsonCodec.write(rule.obligation()));
            value.setTargets(rule.targets().stream().map(target -> {
                var item = new com.xfwang.platform.auth.application.policy.command.PolicyResourceTargetCommand();
                item.setResourceId(target.resourceId());
                item.setIncludeChildren(target.includeChildren());
                return item;
            }).toList());
            return value;
        }).toList());
        return command;
    }

    private PolicyGroupView toView(AuthzPolicyGroup group) {
        boolean sharedVersionPointer = hasSharedVersionPointer(group);
        AuthzPolicyVersion current = sharedVersionPointer || group.getCurrentVersionId() == null ? null
            : versionMapper.selectById(group.getCurrentVersionId());
        AuthzPolicyVersion referencedDraft = group.getDraftVersionId() == null ? null
            : versionMapper.selectById(group.getDraftVersionId());
        AuthzPolicyVersion draft = referencedDraft != null
            && PolicyVersionStatus.DRAFT.code().equals(referencedDraft.getVersionStatus()) ? referencedDraft : null;
        return toView(group, current, draft);
    }

    private PolicyGroupView toView(AuthzPolicyGroup group, AuthzPolicyVersion current, AuthzPolicyVersion draft) {
        AuthzPolicyVersion displayed = draft == null ? current : draft;
        PolicyDocument document = displayed == null ? null : documentCodec.read(displayed.getPolicyDocumentJson());
        return PolicyGroupView.builder()
            .policyGroupId(group.getPolicyGroupId())
            .policyGroupCode(group.getPolicyGroupCode())
            .status(hasSharedVersionPointer(group) && draft != null
                ? PolicyAggregateStatus.DRAFT.code() : group.getStatus())
            .groupLockVersion(group.getLockVersion())
            .policyName(document == null ? null : document.policyName())
            .description(document == null ? null : document.description())
            .effect(document == null ? null : document.effect())
            .riskLevel(document == null ? null : document.riskLevel())
            .priority(document == null ? null : document.priority())
            .hasDraft(draft != null)
            .currentVersionNo(current == null ? null : current.getVersionNo())
            .draftVersionNo(draft == null ? null : draft.getVersionNo())
            .currentVersion(current == null ? null : toVersionView(current))
            .draftVersion(draft == null ? null : toVersionView(draft))
            .deleted("1".equals(group.getDelFlag()))
            .deletedBy(group.getDeletedBy())
            .deletedTime(group.getDeletedTime())
            .deleteReason(group.getDeleteReason())
            .createTime(group.getCreateTime())
            .updateTime(group.getUpdateTime())
            .build();
    }

    private boolean hasSharedVersionPointer(AuthzPolicyGroup group) {
        return group.getCurrentVersionId() != null
            && Objects.equals(group.getCurrentVersionId(), group.getDraftVersionId());
    }

    private PolicyVersionView toVersionView(AuthzPolicyVersion version) {
        return PolicyVersionView.builder()
            .policyGroupVersionId(version.getPolicyGroupVersionId())
            .versionNo(version.getVersionNo())
            .schemaVersion(version.getSchemaVersion())
            .versionStatus(version.getVersionStatus())
            .lockVersion(version.getLockVersion())
            .contentHash(version.getContentHash())
            .document(documentCodec.read(version.getPolicyDocumentJson()))
            .createdBy(version.getCreatedBy())
            .createdTime(version.getCreatedTime())
            .publishedBy(version.getPublishedBy())
            .publishedTime(version.getPublishedTime())
            .build();
    }

    private PolicyOperationLogView toOperationView(AuthzPolicyOperationLog log) {
        PolicyOperationType type = PolicyOperationType.valueOf(log.getOperationType());
        return PolicyOperationLogView.builder()
            .operationLogId(log.getOperationLogId())
            .policyVersionId(log.getPolicyVersionId())
            .operationType(type.code())
            .operationLabel(type.label())
            .fromStatus(log.getFromStatus())
            .toStatus(log.getToStatus())
            .operationReason(log.getOperationReason())
            .operationDetailJson(log.getOperationDetailJson())
            .operatorType(log.getOperatorType())
            .operatorId(log.getOperatorId())
            .traceId(log.getTraceId())
            .occurredTime(log.getOccurredTime())
            .build();
    }

    private void record(AuthzPolicyGroup group, AuthzPolicyVersion version, PolicyOperationType type,
                        String fromStatus, String toStatus, String reason,
                        String beforeHash, String afterHash, SubjectContext actor) {
        AuthzPolicyOperationLog log = new AuthzPolicyOperationLog();
        log.setOperationLogId(IdWorker.getId());
        log.setTenantId(actor.getTenantId());
        log.setPolicyGroupId(group.getPolicyGroupId());
        log.setPolicyGroupCode(group.getPolicyGroupCode());
        log.setPolicyVersionId(version == null ? null : version.getPolicyGroupVersionId());
        log.setOperationType(type.code());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperationReason(reason);
        log.setBeforeContentHash(beforeHash);
        log.setAfterContentHash(afterHash);
        log.setOperatorType(actor.getPrincipalType() == null ? "HUMAN" : actor.getPrincipalType().name());
        log.setOperatorId(StringUtils.isBlank(actor.getPrincipalId())
            ? String.valueOf(actor.getUserId()) : actor.getPrincipalId());
        log.setTraceId(MDC.get("traceId"));
        log.setOccurredTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private AuthzPolicyGroup requireGroup(String groupCode, boolean includeDeleted) {
        if (StringUtils.isBlank(groupCode)) {
            throw validation("策略组编码不能为空");
        }
        AuthzPolicyGroup group = groupMapper.selectOne(new LambdaQueryWrapper<AuthzPolicyGroup>()
            .eq(AuthzPolicyGroup::getPolicyGroupCode, groupCode.trim())
            .eq(!includeDeleted, AuthzPolicyGroup::getDelFlag, "0"));
        if (group == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "策略组不存在");
        }
        return group;
    }

    private AuthzPolicyVersion requireDraft(AuthzPolicyGroup group) {
        if (group.getDraftVersionId() == null) {
            throw validation("策略组不存在待发布版本");
        }
        return requireVersion(group.getDraftVersionId());
    }

    private AuthzPolicyVersion displayedVersion(AuthzPolicyGroup group) {
        Long id = group.getDraftVersionId() == null ? group.getCurrentVersionId() : group.getDraftVersionId();
        return id == null ? null : requireVersion(id);
    }

    private AuthzPolicyVersion requireVersion(Long versionId) {
        AuthzPolicyVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "策略版本不存在");
        }
        return version;
    }

    private SubjectContext actor() {
        SubjectContext actor = subjectProvider.currentSubject();
        if (actor == null || StringUtils.isBlank(actor.getTenantId())) {
            throw validation("当前授权操作主体无效");
        }
        return actor;
    }

    private void requireUpdated(int affected, String message) {
        if (affected != 1) {
            throw validation(message);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ServiceException validation(String message) {
        return new ServiceException(GlobalErrorCode.VALIDATION_ERROR, message);
    }
}

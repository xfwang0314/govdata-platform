package com.xfwang.platform.auth.application.grant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xfwang.platform.auth.application.grant.command.GrantDraftCommand;
import com.xfwang.platform.auth.application.grant.command.GrantScopeCommand;
import com.xfwang.platform.auth.application.grant.view.GrantView;
import com.xfwang.platform.auth.application.grant.view.GrantScopeView;
import com.xfwang.platform.auth.application.grant.query.GrantQuery;
import com.xfwang.platform.api.auth.enums.GrantSourceType;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.AuthorizationObligation;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.domain.entity.AuthzGrant;
import com.xfwang.platform.auth.domain.entity.AuthzGrantScope;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** AuthorizationGrant 生命周期管理实现。 */
@Service
@RequiredArgsConstructor
public class DefaultGrantAdministrationService implements GrantAdministrationService {

    private static final Set<String> GRANTOR_TYPES = Set.of("HUMAN", "SERVICE", "DEPARTMENT", "SYSTEM");
    private static final Set<String> GRANTEE_TYPES = Set.of("HUMAN", "ROLE", "DEPARTMENT");
    private static final Set<String> DEFERRED_GRANTEE_TYPES = Set.of("SERVICE", "AGENT");

    private final AuthzGrantMapper grantMapper;
    private final AuthzGrantScopeMapper scopeMapper;
    private final AuthorizationJsonCodec jsonCodec;
    private final ResourceRegistry resourceRegistry;
    private final AuthorizationCatalog authorizationCatalog;
    private final AuthorizationService authorizationService;
    private final SubjectProvider subjectProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView createDraft(GrantDraftCommand command) {
        validate(command);
        SubjectContext actor = subjectProvider.currentSubject();
        if (grantMapper.selectCount(new LambdaQueryWrapper<AuthzGrant>()
            .eq(AuthzGrant::getSourceType, normalize(command.getSourceType()))
            .eq(AuthzGrant::getSourceId, command.getSourceId().trim())) > 0) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "该业务来源已生成Grant");
        }

        long grantId = IdWorker.getId();
        AuthzGrant grant = new AuthzGrant();
        grant.setGrantId(grantId);
        grant.setGrantCode("grt_" + Long.toUnsignedString(grantId, 36));
        grant.setSourceType(normalize(command.getSourceType()));
        grant.setSourceId(command.getSourceId().trim());
        grant.setGrantorType(normalize(command.getGrantorType()));
        grant.setGrantorId(command.getGrantorId().trim());
        grant.setGranteeType(normalize(command.getGranteeType()));
        grant.setGranteeId(command.getGranteeId().trim());
        grant.setPurpose(normalize(command.getPurpose()));
        grant.setEffectiveTime(command.getEffectiveTime());
        grant.setExpireTime(command.getExpireTime());
        grant.setConditionJson(command.getConditionJson());
        grant.setStatus("DRAFT");
        grant.setLockVersion(0);
        grant.setDelFlag("0");
        grant.setCreateBy(actor.getUserId());
        grant.setCreateDept(actor.getDeptId());
        grant.setUpdateBy(actor.getUserId());
        grantMapper.insert(grant);
        command.getScopes().forEach(value -> insertScope(
            grant.getGrantId(), grant.getPurpose(), value, actor.getUserId()));
        return toView(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView updateDraft(Long grantId, GrantDraftCommand command) {
        validate(command);
        AuthzGrant grant = requireGrant(grantId);
        if (!Set.of("DRAFT", "PENDING").contains(grant.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "只有草稿或待审批Grant可以修改");
        }
        SubjectContext actor = subjectProvider.currentSubject();
        grant.setGranteeType(normalize(command.getGranteeType()));
        grant.setGranteeId(command.getGranteeId().trim());
        grant.setPurpose(normalize(command.getPurpose()));
        grant.setEffectiveTime(command.getEffectiveTime());
        grant.setExpireTime(command.getExpireTime());
        grant.setConditionJson(command.getConditionJson());
        grant.setUpdateBy(actor.getUserId());
        grant.setUpdateTime(LocalDateTime.now());
        grantMapper.updateById(grant);

        // 重建 Scope 范围
        scopeMapper.delete(new LambdaQueryWrapper<AuthzGrantScope>()
            .eq(AuthzGrantScope::getGrantId, grantId));
        command.getScopes().forEach(value -> insertScope(
            grant.getGrantId(), grant.getPurpose(), value, actor.getUserId()));
        return toView(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView activate(Long grantId) {
        AuthzGrant grant = requireGrant(grantId);
        if (!Set.of("DRAFT", "PENDING").contains(grant.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "只有草稿或待审批Grant可以激活");
        }
        List<AuthzGrantScope> scopes = scopeMapper.selectList(new LambdaQueryWrapper<AuthzGrantScope>()
            .eq(AuthzGrantScope::getGrantId, grantId));
        if (scopes.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant没有资源动作范围");
        }
        if (grant.getExpireTime() != null && !grant.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant已经过期");
        }
        verifyGrantorAuthority(grant, scopes);
        grant.setStatus("ACTIVE");
        grantMapper.updateById(grant);
        return toView(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView revoke(Long grantId, String reason) {
        SubjectContext actor = subjectProvider.currentSubject();
        AuthzGrant grant = requireGrant(grantId);
        if (!"ACTIVE".equals(grant.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "只有生效中的Grant可以撤销");
        }
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "撤销原因不能为空");
        }
        grant.setStatus("REVOKED");
        grant.setRevokedBy(actor.getUserId());
        grant.setRevokedTime(LocalDateTime.now());
        grant.setRevokeReason(reason.trim());
        grantMapper.updateById(grant);
        return toView(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long grantId, String reason) {
        AuthzGrant grant = requireGrant(grantId);
        SubjectContext actor = subjectProvider.currentSubject();
        String deleteReason = StringUtils.isBlank(reason) ? "管理员删除" : reason.trim();
        if ("ACTIVE".equals(grant.getStatus())) {
            grant.setStatus("REVOKED");
            grant.setRevokedBy(actor.getUserId());
            grant.setRevokedTime(LocalDateTime.now());
            grant.setRevokeReason(deleteReason);
        }
        grant.setDelFlag("1");
        grant.setUpdateBy(actor.getUserId());
        grantMapper.updateById(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView restore(Long grantId) {
        AuthzGrant grant = requireGrant(grantId);
        if (!"1".equals(grant.getDelFlag())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "共享授权未被删除");
        }
        SubjectContext actor = subjectProvider.currentSubject();
        grant.setDelFlag("0");
        grant.setUpdateBy(actor.getUserId());
        grantMapper.updateById(grant);
        return toView(grant);
    }

    @Override
    public GrantView get(Long grantId) {
        return toView(requireGrant(grantId));
    }

    @Override
    public PageResult<GrantView> page(GrantQuery query, PageQuery pageQuery) {
        GrantQuery filter = query == null ? new GrantQuery() : query;
        LambdaQueryWrapper<AuthzGrant> wrapper = new LambdaQueryWrapper<AuthzGrant>()
            .eq(AuthzGrant::getDelFlag, Boolean.TRUE.equals(filter.getDeleted()) ? "1" : "0")
            .and(StringUtils.isNotBlank(filter.getKeyword()), value -> value
                .like(AuthzGrant::getGrantCode, filter.getKeyword())
                .or().like(AuthzGrant::getGranteeId, filter.getKeyword())
                .or().like(AuthzGrant::getSourceId, filter.getKeyword()))
            .eq(StringUtils.isNotBlank(filter.getSourceType()), AuthzGrant::getSourceType,
                filter.getSourceType())
            .eq(StringUtils.isNotBlank(filter.getGranteeType()), AuthzGrant::getGranteeType,
                filter.getGranteeType())
            .eq(StringUtils.isNotBlank(filter.getPurpose()), AuthzGrant::getPurpose, filter.getPurpose())
            .eq(StringUtils.isNotBlank(filter.getStatus()), AuthzGrant::getStatus, filter.getStatus())
            .orderByDesc(AuthzGrant::getCreateTime)
            .orderByDesc(AuthzGrant::getGrantId);
        Page<AuthzGrant> page = grantMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords().stream().map(this::toView).toList(), page.getTotal());
    }

    private void validate(GrantDraftCommand command) {
        if (command == null || StringUtils.isAnyBlank(command.getSourceType(),
            command.getSourceId(), command.getGrantorType(), command.getGrantorId(), command.getGranteeType(),
            command.getGranteeId(), command.getPurpose()) || command.getEffectiveTime() == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant必填信息不完整");
        }
        String granteeType = normalize(command.getGranteeType());
        if (DEFERRED_GRANTEE_TYPES.contains(granteeType)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "SERVICE/AGENT主体授权尚未启用，必须通过可信主体认证和Agent Delegation后接入");
        }
        if (!isGrantSourceType(command.getSourceType())
            || !GRANTOR_TYPES.contains(normalize(command.getGrantorType()))
            || !GRANTEE_TYPES.contains(granteeType)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant主体或来源类型无效");
        }
        if (command.getExpireTime() != null && !command.getExpireTime().isAfter(command.getEffectiveTime())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "失效时间必须晚于生效时间");
        }
        if (command.getScopes() == null || command.getScopes().isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant范围不能为空");
        }
        if (authorizationCatalog.findPurpose(normalize(command.getPurpose())).isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant用途未在授权目录注册");
        }
        try {
            if (StringUtils.isNotBlank(command.getConditionJson())) {
                jsonCodec.read(command.getConditionJson(), PolicyConditionGroup.class);
            }
            command.getScopes().stream()
                .map(GrantScopeCommand::getObligationJson)
                .filter(StringUtils::isNotBlank)
                .forEach(value -> jsonCodec.read(value, AuthorizationObligation.class));
        } catch (RuntimeException ex) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant条件或约束不是合法 JSON");
        }
    }

    private void insertScope(Long grantId, String purpose, GrantScopeCommand command, Long actorId) {
        if (command == null || command.getResourceId() == null || StringUtils.isBlank(command.getActionCode())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant资源和动作不能为空");
        }
        ResourceDescriptor resource = resourceRegistry.findById(command.getResourceId())
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.NOT_FOUND, "Grant绑定的授权资源不存在"));
        boolean grantSupported = authorizationCatalog.findResourceType(resource.getResourceType())
            .map(definition -> definition.capabilities().contains(ResourceAuthorizationCapability.GRANT))
            .orElse(false);
        if (!grantSupported) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "该资源类型不支持共享授权");
        }
        String action = normalize(command.getActionCode());
        if (!authorizationCatalog.supportsAction(resource.getResourceType(), action)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant动作不属于资源类型授权目录");
        }
        if (!authorizationCatalog.supportsPurpose(resource.getResourceType(), normalize(purpose))) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant用途不适用于当前资源类型");
        }
        AuthzGrantScope scope = new AuthzGrantScope();
        scope.setGrantId(grantId);
        scope.setResourceId(command.getResourceId());
        scope.setActionCode(action);
        scope.setIncludeChildren(Boolean.TRUE.equals(command.getIncludeChildren()));
        scope.setObligationJson(command.getObligationJson());
        scope.setCreateBy(actorId);
        scope.setCreateTime(LocalDateTime.now());
        scopeMapper.insert(scope);
    }

    private AuthzGrant requireGrant(Long grantId) {
        AuthzGrant grant = grantId == null ? null : grantMapper.selectById(grantId);
        if (grant == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "Grant不存在");
        }
        return grant;
    }

    private GrantView toView(AuthzGrant grant) {
        List<GrantScopeView> scopes = scopeMapper.selectList(new LambdaQueryWrapper<AuthzGrantScope>()
                .eq(AuthzGrantScope::getGrantId, grant.getGrantId()))
            .stream().map(scope -> {
                ResourceDescriptor resource = resourceRegistry.findById(scope.getResourceId()).orElse(null);
                return GrantScopeView.builder()
                    .scopeId(scope.getScopeId()).resourceId(scope.getResourceId())
                    .resourceCode(resource == null ? null : resource.getResourceCode())
                    .resourceName(resource == null ? null : resource.getResourceName())
                    .actionCode(scope.getActionCode()).includeChildren(scope.getIncludeChildren())
                    .obligationJson(scope.getObligationJson()).build();
            }).toList();
        return GrantView.builder()
            .grantId(grant.getGrantId())
            .grantCode(grant.getGrantCode())
            .sourceType(grant.getSourceType())
            .sourceId(grant.getSourceId())
            .granteeType(grant.getGranteeType())
            .granteeId(grant.getGranteeId())
            .purpose(grant.getPurpose())
            .effectiveTime(grant.getEffectiveTime())
            .expireTime(grant.getExpireTime())
            .status(grant.getStatus())
            .grantorType(grant.getGrantorType())
            .grantorId(grant.getGrantorId())
            .conditionJson(grant.getConditionJson())
            .revokeReason(grant.getRevokeReason())
            .scopes(scopes)
            .createTime(grant.getCreateTime())
            .updateTime(grant.getUpdateTime())
            .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isGrantSourceType(String sourceType) {
        try {
            GrantSourceType.valueOf(normalize(sourceType));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void verifyGrantorAuthority(AuthzGrant grant, List<AuthzGrantScope> scopes) {
        for (AuthzGrantScope scope : scopes) {
            ResourceDescriptor resource = resourceRegistry.findById(scope.getResourceId())
                .orElseThrow(() -> new ServiceException(GlobalErrorCode.NOT_FOUND, "Grant绑定的授权资源不存在"));
            AccessDecision decision = authorizationService.decide(AccessRequest.builder()
                .resourceId(resource.getResourceId())
                .resourceType(resource.getResourceType())
                .action("AUTHORIZE")
                .environment(EnvironmentContext.builder()
                    .purpose(grant.getPurpose())
                    .requestTime(LocalDateTime.now())
                    .build())
                .build());
            if (!decision.isAllowed()) {
                throw new ServiceException(GlobalErrorCode.FORBIDDEN, "当前主体无权转授 Grant 中的资源");
            }
        }
    }
}

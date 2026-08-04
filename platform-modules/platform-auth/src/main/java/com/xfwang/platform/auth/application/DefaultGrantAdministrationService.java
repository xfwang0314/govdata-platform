package com.xfwang.platform.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.auth.application.model.GrantDraftCommand;
import com.xfwang.platform.auth.application.model.GrantScopeCommand;
import com.xfwang.platform.auth.application.model.GrantView;
import com.xfwang.platform.auth.api.model.AuthorizationObligation;
import com.xfwang.platform.auth.api.service.ResourceRegistry;
import com.xfwang.platform.auth.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.domain.AuthzGrant;
import com.xfwang.platform.auth.domain.AuthzGrantScope;
import com.xfwang.platform.auth.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.policy.model.PolicyConditionGroup;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/** AuthorizationGrant 生命周期管理实现。 */
@Service
@RequiredArgsConstructor
public class DefaultGrantAdministrationService implements GrantAdministrationService {

    private static final Set<String> SOURCE_TYPES = Set.of("SHARING", "ADMIN", "SUBSCRIPTION", "DELEGATION", "SYSTEM");
    private static final Set<String> GRANTOR_TYPES = Set.of("HUMAN", "SERVICE", "DEPARTMENT", "SYSTEM");
    private static final Set<String> GRANTEE_TYPES = Set.of("HUMAN", "ROLE", "DEPARTMENT");
    private static final Set<String> DEFERRED_GRANTEE_TYPES = Set.of("SERVICE", "AGENT");

    private final AuthzGrantMapper grantMapper;
    private final AuthzGrantScopeMapper scopeMapper;
    private final AuthorizationJsonCodec jsonCodec;
    private final ResourceRegistry resourceRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView createDraft(GrantDraftCommand command) {
        validate(command);
        if (grantMapper.selectCount(new LambdaQueryWrapper<AuthzGrant>()
            .eq(AuthzGrant::getGrantNo, command.getGrantNo().trim())) > 0) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant编号已存在");
        }
        if (grantMapper.selectCount(new LambdaQueryWrapper<AuthzGrant>()
            .eq(AuthzGrant::getSourceType, normalize(command.getSourceType()))
            .eq(AuthzGrant::getSourceId, command.getSourceId().trim())) > 0) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "该业务来源已生成Grant");
        }

        AuthzGrant grant = new AuthzGrant();
        grant.setGrantNo(command.getGrantNo().trim());
        grant.setSourceType(normalize(command.getSourceType()));
        grant.setSourceId(command.getSourceId().trim());
        grant.setGrantorType(normalize(command.getGrantorType()));
        grant.setGrantorId(command.getGrantorId().trim());
        grant.setGranteeType(normalize(command.getGranteeType()));
        grant.setGranteeId(command.getGranteeId().trim());
        grant.setPurpose(command.getPurpose().trim());
        grant.setEffectiveTime(command.getEffectiveTime());
        grant.setExpireTime(command.getExpireTime());
        grant.setConditionJson(command.getConditionJson());
        grant.setStatus("DRAFT");
        grant.setLockVersion(0);
        grant.setDelFlag("0");
        grantMapper.insert(grant);
        command.getScopes().forEach(value -> insertScope(grant.getGrantId(), value));
        return toView(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView activate(Long grantId) {
        AuthzGrant grant = requireGrant(grantId);
        if (!Set.of("DRAFT", "PENDING").contains(grant.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "只有草稿或待审批Grant可以激活");
        }
        if (scopeMapper.selectCount(new LambdaQueryWrapper<AuthzGrantScope>()
            .eq(AuthzGrantScope::getGrantId, grantId)) == 0) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant没有资源动作范围");
        }
        if (grant.getExpireTime() != null && !grant.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant已经过期");
        }
        grant.setStatus("ACTIVE");
        grantMapper.updateById(grant);
        return toView(grant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantView revoke(Long grantId, String reason) {
        AuthzGrant grant = requireGrant(grantId);
        if (!"ACTIVE".equals(grant.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "只有生效中的Grant可以撤销");
        }
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "撤销原因不能为空");
        }
        grant.setStatus("REVOKED");
        grant.setRevokedTime(LocalDateTime.now());
        grant.setRevokeReason(reason.trim());
        grantMapper.updateById(grant);
        return toView(grant);
    }

    @Override
    public GrantView get(Long grantId) {
        return toView(requireGrant(grantId));
    }

    private void validate(GrantDraftCommand command) {
        if (command == null || StringUtils.isAnyBlank(command.getGrantNo(), command.getSourceType(),
            command.getSourceId(), command.getGrantorType(), command.getGrantorId(), command.getGranteeType(),
            command.getGranteeId(), command.getPurpose()) || command.getEffectiveTime() == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant必填信息不完整");
        }
        String granteeType = normalize(command.getGranteeType());
        if (DEFERRED_GRANTEE_TYPES.contains(granteeType)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "SERVICE/AGENT主体授权尚未启用，必须通过可信主体认证和Agent Delegation后接入");
        }
        if (!SOURCE_TYPES.contains(normalize(command.getSourceType()))
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

    private void insertScope(Long grantId, GrantScopeCommand command) {
        if (command == null || command.getResourceId() == null || StringUtils.isBlank(command.getActionCode())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Grant资源和动作不能为空");
        }
        if (resourceRegistry.findById(command.getResourceId()).isEmpty()) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "Grant绑定的授权资源不存在");
        }
        AuthzGrantScope scope = new AuthzGrantScope();
        scope.setGrantId(grantId);
        scope.setResourceId(command.getResourceId());
        scope.setActionCode(normalize(command.getActionCode()));
        scope.setIncludeChildren(Boolean.TRUE.equals(command.getIncludeChildren()));
        scope.setObligationJson(command.getObligationJson());
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
        return GrantView.builder()
            .grantId(grant.getGrantId())
            .grantNo(grant.getGrantNo())
            .sourceType(grant.getSourceType())
            .sourceId(grant.getSourceId())
            .granteeType(grant.getGranteeType())
            .granteeId(grant.getGranteeId())
            .purpose(grant.getPurpose())
            .effectiveTime(grant.getEffectiveTime())
            .expireTime(grant.getExpireTime())
            .status(grant.getStatus())
            .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

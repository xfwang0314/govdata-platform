package com.xfwang.platform.auth.application.decision;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.enums.GrantStatus;
import com.xfwang.platform.api.auth.enums.PolicyEffect;
import com.xfwang.platform.api.auth.enums.SubjectType;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.AuthorizationObligation;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.domain.entity.AuthzDecisionLog;
import com.xfwang.platform.auth.domain.entity.AuthzGrant;
import com.xfwang.platform.auth.domain.entity.AuthzGrantScope;
import com.xfwang.platform.auth.domain.entity.AuthzPolicy;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyAction;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyResource;
import com.xfwang.platform.auth.domain.entity.AuthzPolicySubject;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyVersion;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzDecisionLogMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyActionMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyResourceMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicySubjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyVersionMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import com.xfwang.platform.auth.domain.policy.PolicyConditionEvaluator;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 默认拒绝、明确拒绝优先的通用授权决策服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationService implements AuthorizationService {

    private static final String ACTIVE = "ACTIVE";
    private static final int MAX_RESOURCE_DEPTH = 32;

    private final SubjectProvider subjectProvider;
    private final ResourceRegistry resourceRegistry;
    private final AuthzResourceMapper resourceMapper;
    private final AuthzPolicyMapper policyMapper;
    private final AuthzPolicyVersionMapper policyVersionMapper;
    private final AuthzPolicySubjectMapper policySubjectMapper;
    private final AuthzPolicyResourceMapper policyResourceMapper;
    private final AuthzPolicyActionMapper policyActionMapper;
    private final AuthzGrantMapper grantMapper;
    private final AuthzGrantScopeMapper grantScopeMapper;
    private final AuthzDecisionLogMapper decisionLogMapper;
    private final PolicyConditionEvaluator conditionEvaluator;
    private final AuthorizationObligationMerger obligationMerger;
    private final AuthorizationJsonCodec jsonCodec;

    @Override
    public AccessDecision decide(AccessRequest request) {
        long start = System.currentTimeMillis();
        if (request == null || request.getResourceId() == null || StringUtils.isBlank(request.getAction())) {
            return audited(request, null, deny("AUTHZ_REQUEST_INVALID", "资源和动作不能为空"), start);
        }
        SubjectContext subject = request.getSubject() == null
            ? subjectProvider.currentSubject()
            : request.getSubject();
        request.setSubject(subject);
        if (request.getEnvironment() == null) {
            request.setEnvironment(EnvironmentContext.builder().requestTime(LocalDateTime.now()).build());
        } else if (request.getEnvironment().getRequestTime() == null) {
            request.getEnvironment().setRequestTime(LocalDateTime.now());
        }

        ResourceDescriptor resource = resourceRegistry.findById(request.getResourceId()).orElse(null);
        if (resource == null) {
            return audited(request, null, deny("AUTHZ_RESOURCE_NOT_FOUND", "授权资源不存在"), start);
        }
        if (!StringUtils.equals(subject.getTenantId(), resource.getTenantId())) {
            return audited(request, resource, deny("AUTHZ_TENANT_MISMATCH", "禁止跨租户访问资源"), start);
        }
        if (!ACTIVE.equals(resource.getStatus())) {
            return audited(request, resource, deny("AUTHZ_RESOURCE_DISABLED", "授权资源已停用"), start);
        }
        if (StringUtils.isNotBlank(request.getResourceType())
            && !StringUtils.equals(request.getResourceType(), resource.getResourceType())) {
            return audited(request, resource, deny("AUTHZ_RESOURCE_TYPE_MISMATCH", "资源类型不匹配"), start);
        }

        Set<Long> lineage = loadLineage(resource);
        List<ResolvedPolicy> policies = loadPolicies(resource, lineage);
        Map<Long, List<AuthzPolicySubject>> subjectBindings = loadPolicySubjects(policies);
        Map<Long, Set<String>> actionBindings = loadPolicyActions(policies);
        List<Long> permitPolicyIds = new ArrayList<>();
        List<Long> denyPolicyIds = new ArrayList<>();
        List<AuthorizationObligation> obligations = new ArrayList<>();

        for (ResolvedPolicy resolved : policies) {
            AuthzPolicy policy = resolved.policy();
            AuthzPolicyVersion version = resolved.version();
            if (!supportsAction(actionBindings.get(version.getPolicyVersionId()), request.getAction())
                || !matchesSubject(subjectBindings.get(version.getPolicyVersionId()), subject)
                || !matchesConditions(version.getConditionJson(), subject, resource, request.getEnvironment())) {
                continue;
            }
            if (PolicyEffect.DENY.name().equalsIgnoreCase(version.getEffect())) {
                denyPolicyIds.add(version.getPolicyVersionId());
                continue;
            }
            if (PolicyEffect.PERMIT.name().equalsIgnoreCase(version.getEffect())) {
                AuthorizationObligation obligation = parseObligation(policy, version);
                if (obligation == null && StringUtils.isNotBlank(version.getObligationJson())) {
                    continue;
                }
                permitPolicyIds.add(version.getPolicyVersionId());
                if (obligation != null) {
                    obligations.add(obligation);
                }
            }
        }

        if (!denyPolicyIds.isEmpty()) {
            AccessDecision decision = deny("AUTHZ_EXPLICIT_DENY", "命中明确拒绝策略");
            decision.setMatchedPolicyIds(denyPolicyIds);
            return audited(request, resource, decision, start);
        }

        GrantMatch grantMatch = loadMatchingGrants(request, resource, lineage);
        List<Long> grantIds = grantMatch.grantIds();
        obligations.addAll(grantMatch.obligations());
        if (permitPolicyIds.isEmpty() && grantIds.isEmpty()) {
            return audited(request, resource,
                deny("AUTHZ_DEFAULT_DENY", "没有匹配到允许策略或有效 Grant"), start);
        }

        AccessDecision decision = AccessDecision.builder()
            .decision(DecisionType.PERMIT)
            .reasonCode(grantIds.isEmpty() ? "AUTHZ_POLICY_PERMIT" : "AUTHZ_GRANT_PERMIT")
            .reason(grantIds.isEmpty() ? "命中允许策略" : "命中租户内有效 Grant")
            .matchedPolicyIds(permitPolicyIds)
            .matchedGrantIds(grantIds)
            .obligation(obligationMerger.merge(obligations))
            .build();
        return audited(request, resource, decision, start);
    }

    @Override
    public void check(AccessRequest request) {
        AccessDecision decision = decide(request);
        if (!decision.isAllowed()) {
            throw new ServiceException(GlobalErrorCode.FORBIDDEN,
                decision.getReasonCode() + ": " + decision.getReason());
        }
    }

    private Set<Long> loadLineage(ResourceDescriptor resource) {
        Set<Long> result = new LinkedHashSet<>();
        result.add(resource.getResourceId());
        AuthzResource current = resourceMapper.selectById(resource.getResourceId());
        if (current == null || !Boolean.TRUE.equals(current.getInheritPolicy())) {
            return result;
        }
        Long parentId = resource.getParentId();
        int depth = 0;
        while (parentId != null && depth++ < MAX_RESOURCE_DEPTH && result.add(parentId)) {
            AuthzResource parent = resourceMapper.selectById(parentId);
            parentId = parent == null || !Boolean.TRUE.equals(parent.getInheritPolicy())
                ? null
                : parent.getParentId();
        }
        return result;
    }

    private List<ResolvedPolicy> loadPolicies(ResourceDescriptor resource, Set<Long> lineage) {
        List<AuthzPolicyResource> bindings = policyResourceMapper.selectList(
            new LambdaQueryWrapper<AuthzPolicyResource>()
                .and(wrapper -> wrapper.in(AuthzPolicyResource::getResourceId, lineage)
                    .or(type -> type.isNull(AuthzPolicyResource::getResourceId)
                        .eq(AuthzPolicyResource::getResourceType, resource.getResourceType()))));
        Set<Long> policyIds = bindings.stream()
            .filter(binding -> Objects.equals(binding.getResourceId(), resource.getResourceId())
                || binding.getResourceId() == null
                || Boolean.TRUE.equals(binding.getIncludeChildren()))
            .map(AuthzPolicyResource::getPolicyVersionId)
            .collect(Collectors.toSet());
        if (policyIds.isEmpty()) {
            return List.of();
        }
        List<AuthzPolicyVersion> versions = policyVersionMapper.selectList(
            new LambdaQueryWrapper<AuthzPolicyVersion>()
                .in(AuthzPolicyVersion::getPolicyVersionId, policyIds)
                .eq(AuthzPolicyVersion::getVersionStatus, "PUBLISHED"));
        if (versions.isEmpty()) {
            return List.of();
        }
        Set<Long> aggregateIds = versions.stream().map(AuthzPolicyVersion::getPolicyId).collect(Collectors.toSet());
        Map<Long, AuthzPolicy> aggregates = policyMapper.selectList(new LambdaQueryWrapper<AuthzPolicy>()
                .in(AuthzPolicy::getPolicyId, aggregateIds)
                .eq(AuthzPolicy::getStatus, "PUBLISHED"))
            .stream().collect(Collectors.toMap(AuthzPolicy::getPolicyId, Function.identity()));
        return versions.stream()
            .filter(version -> {
                AuthzPolicy policy = aggregates.get(version.getPolicyId());
                return policy != null && Objects.equals(policy.getCurrentVersionId(), version.getPolicyVersionId());
            })
            .map(version -> new ResolvedPolicy(aggregates.get(version.getPolicyId()), version))
            .sorted((left, right) -> Integer.compare(priority(right.version()), priority(left.version())))
            .toList();
    }

    private Map<Long, List<AuthzPolicySubject>> loadPolicySubjects(List<ResolvedPolicy> policies) {
        if (policies.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = policies.stream().map(value -> value.version().getPolicyVersionId()).collect(Collectors.toSet());
        return policySubjectMapper.selectList(new LambdaQueryWrapper<AuthzPolicySubject>()
                .in(AuthzPolicySubject::getPolicyVersionId, ids))
            .stream().collect(Collectors.groupingBy(AuthzPolicySubject::getPolicyVersionId));
    }

    private Map<Long, Set<String>> loadPolicyActions(List<ResolvedPolicy> policies) {
        if (policies.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = policies.stream().map(value -> value.version().getPolicyVersionId()).collect(Collectors.toSet());
        return policyActionMapper.selectList(new LambdaQueryWrapper<AuthzPolicyAction>()
                .in(AuthzPolicyAction::getPolicyVersionId, ids))
            .stream().collect(Collectors.groupingBy(AuthzPolicyAction::getPolicyVersionId,
                Collectors.mapping(AuthzPolicyAction::getActionCode, Collectors.toSet())));
    }

    private GrantMatch loadMatchingGrants(AccessRequest request, ResourceDescriptor resource, Set<Long> lineage) {
        LocalDateTime now = request.getEnvironment().getRequestTime();
        List<AuthzGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<AuthzGrant>()
            .eq(AuthzGrant::getStatus, GrantStatus.ACTIVE.name())
            .le(AuthzGrant::getEffectiveTime, now)
            .and(wrapper -> wrapper.isNull(AuthzGrant::getExpireTime)
                .or().ge(AuthzGrant::getExpireTime, now)));
        List<AuthzGrant> subjectGrants = grants.stream()
            .filter(grant -> matchesSubject(grant.getGranteeType(), grant.getGranteeId(), request.getSubject()))
            .filter(grant -> StringUtils.isBlank(grant.getPurpose())
                || StringUtils.equals(grant.getPurpose(), request.getEnvironment().getPurpose()))
            .filter(grant -> matchesConditions(grant.getConditionJson(), request.getSubject(),
                resource, request.getEnvironment()))
            .toList();
        if (subjectGrants.isEmpty()) {
            return GrantMatch.empty();
        }
        Set<Long> grantIds = subjectGrants.stream().map(AuthzGrant::getGrantId).collect(Collectors.toSet());
        Map<Long, AuthzGrant> byId = subjectGrants.stream()
            .collect(Collectors.toMap(AuthzGrant::getGrantId, Function.identity()));
        List<AuthzGrantScope> scopes = grantScopeMapper.selectList(new LambdaQueryWrapper<AuthzGrantScope>()
            .in(AuthzGrantScope::getGrantId, grantIds)
            .in(AuthzGrantScope::getResourceId, lineage));
        List<AuthzGrantScope> matches = scopes.stream()
            .filter(binding -> Objects.equals(binding.getResourceId(), resource.getResourceId())
                || Boolean.TRUE.equals(binding.getIncludeChildren()))
            .filter(binding -> StringUtils.equalsIgnoreCase(binding.getActionCode(), request.getAction()))
            .toList();
        List<AuthzGrantScope> validMatches = matches.stream()
            .filter(scope -> StringUtils.isBlank(scope.getObligationJson()) || parseObligation(scope) != null)
            .toList();
        List<Long> matchedIds = validMatches.stream().map(AuthzGrantScope::getGrantId)
            .filter(byId::containsKey)
            .distinct()
            .toList();
        List<AuthorizationObligation> grantObligations = validMatches.stream()
            .map(this::parseObligation)
            .filter(Objects::nonNull)
            .toList();
        return new GrantMatch(matchedIds, grantObligations);
    }

    private boolean matchesSubject(Collection<AuthzPolicySubject> bindings, SubjectContext subject) {
        return bindings != null && bindings.stream()
            .anyMatch(binding -> matchesSubject(binding.getSubjectType(), binding.getSubjectId(), subject));
    }

    private boolean matchesSubject(String typeValue, String id, SubjectContext subject) {
        if (StringUtils.isBlank(typeValue)) {
            return false;
        }
        SubjectType type;
        try {
            type = SubjectType.valueOf(typeValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return switch (type) {
            case ANY -> true;
            case HUMAN -> subject.getUserId() != null && StringUtils.equals(id, subject.getUserId().toString());
            case ROLE -> safe(subject.getRoleKeys()).contains(id)
                || safe(subject.getRoleIds()).stream().map(String::valueOf).anyMatch(value -> value.equals(id));
            case DEPARTMENT -> safe(subject.getDeptPathIds()).stream()
                .map(String::valueOf).anyMatch(value -> value.equals(id));
        };
    }

    private <T> Set<T> safe(Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private boolean matchesConditions(String json, SubjectContext subject,
                                      ResourceDescriptor resource, EnvironmentContext environment) {
        if (StringUtils.isBlank(json)) {
            return true;
        }
        try {
            PolicyConditionGroup group = jsonCodec.read(json, PolicyConditionGroup.class);
            return conditionEvaluator.matches(group, subject, resource, environment);
        } catch (RuntimeException ex) {
            log.warn("授权条件配置无效，按不匹配处理", ex);
            return false;
        }
    }

    private AuthorizationObligation parseObligation(AuthzPolicy policy, AuthzPolicyVersion version) {
        if (StringUtils.isBlank(version.getObligationJson())) {
            return null;
        }
        try {
            return jsonCodec.read(version.getObligationJson(), AuthorizationObligation.class);
        } catch (RuntimeException ex) {
            log.warn("策略 {} 的 obligation 配置无效，按不匹配处理", policy.getPolicyCode(), ex);
            return null;
        }
    }

    private AuthorizationObligation parseObligation(AuthzGrantScope scope) {
        if (StringUtils.isBlank(scope.getObligationJson())) {
            return null;
        }
        try {
            return jsonCodec.read(scope.getObligationJson(), AuthorizationObligation.class);
        } catch (RuntimeException ex) {
            log.warn("Grant scope {} 的 obligation 配置无效，按无效范围处理", scope.getScopeId(), ex);
            return null;
        }
    }

    private boolean supportsAction(Set<String> actions, String action) {
        return actions != null && actions.stream().anyMatch(value -> value.equalsIgnoreCase(action));
    }

    private int priority(AuthzPolicyVersion version) {
        return version.getPriority() == null ? 0 : version.getPriority();
    }

    private AccessDecision deny(String code, String reason) {
        return AccessDecision.builder()
            .decision(DecisionType.DENY)
            .reasonCode(code)
            .reason(reason)
            .obligation(AuthorizationObligation.builder().build())
            .build();
    }

    private AccessDecision audited(AccessRequest request, ResourceDescriptor resource,
                                   AccessDecision decision, long start) {
        try {
            AuthzDecisionLog audit = new AuthzDecisionLog();
            SubjectContext subject = request == null ? null : request.getSubject();
            String resourceTenantId = resource == null ? subject == null ? null : subject.getTenantId() : resource.getTenantId();
            audit.setTenantId(resourceTenantId);
            audit.setSubjectTenantId(subject == null ? resourceTenantId : subject.getTenantId());
            audit.setSubjectType("HUMAN");
            audit.setSubjectId(subject == null || subject.getUserId() == null ? "ANONYMOUS" : subject.getUserId().toString());
            audit.setSubjectSnapshotJson(jsonCodec.write(Map.of(
                "userId", subject == null ? "" : Objects.toString(subject.getUserId(), ""),
                "deptId", subject == null ? "" : Objects.toString(subject.getDeptId(), ""))));
            audit.setResourceTenantId(resourceTenantId);
            audit.setResourceId(request == null ? null : request.getResourceId());
            audit.setResourceTypeSnapshot(resource == null
                ? request == null || StringUtils.isBlank(request.getResourceType()) ? "UNKNOWN" : request.getResourceType()
                : resource.getResourceType());
            audit.setResourceCodeSnapshot(resource == null ? "UNKNOWN" : resource.getResourceCode());
            audit.setActionCode(request == null || StringUtils.isBlank(request.getAction()) ? "UNKNOWN" : request.getAction());
            audit.setDecision(decision.getDecision().name());
            audit.setReasonCode(decision.getReasonCode());
            audit.setPolicyVersionIdsJson(jsonCodec.write(decision.getMatchedPolicyIds()));
            audit.setGrantIdsJson(jsonCodec.write(decision.getMatchedGrantIds()));
            audit.setObligationSummaryJson(jsonCodec.write(decision.getObligation()));
            EnvironmentContext environment = request == null ? null : request.getEnvironment();
            audit.setPurpose(environment == null ? null : environment.getPurpose());
            String traceId = environment == null || StringUtils.isBlank(environment.getTraceId())
                ? MDC.get("traceId")
                : environment.getTraceId();
            audit.setTraceId(StringUtils.isBlank(traceId) ? java.util.UUID.randomUUID().toString().replace("-", "") : traceId);
            audit.setDurationMs(System.currentTimeMillis() - start);
            audit.setOccurredTime(LocalDateTime.now());
            decisionLogMapper.insert(audit);
        } catch (RuntimeException ex) {
            log.error("写入授权决策审计失败，decision={}", decision.getReasonCode(), ex);
        }
        return decision;
    }

    private record ResolvedPolicy(AuthzPolicy policy, AuthzPolicyVersion version) {
    }

    private record GrantMatch(List<Long> grantIds, List<AuthorizationObligation> obligations) {

        private static GrantMatch empty() {
            return new GrantMatch(List.of(), List.of());
        }
    }
}

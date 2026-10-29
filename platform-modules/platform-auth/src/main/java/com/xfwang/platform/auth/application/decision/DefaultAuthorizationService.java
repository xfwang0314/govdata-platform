package com.xfwang.platform.auth.application.decision;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.enums.GrantStatus;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.AuthorizationObligation;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.domain.entity.AuthzDecisionLog;
import com.xfwang.platform.auth.domain.entity.AuthzGrant;
import com.xfwang.platform.auth.domain.entity.AuthzGrantScope;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeResource;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeRule;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeSubject;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.domain.policy.PolicyConditionEvaluator;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import com.xfwang.platform.auth.domain.service.AdministratorAuthorizationPolicy;
import com.xfwang.platform.auth.domain.exception.AuthorizationErrorCode;
import com.xfwang.platform.auth.domain.service.SubjectSelectorMatcher;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzDecisionLogMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeResourceMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 默认拒绝、显式拒绝优先的当前策略组版本决策引擎。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationService implements AuthorizationService {

    private static final String ACTIVE = "ACTIVE";
    private static final int MAX_RESOURCE_DEPTH = 32;

    private final SubjectProvider subjectProvider;
    private final ResourceRegistry resourceRegistry;
    private final AuthzResourceMapper resourceMapper;
    private final AuthzPolicyRuntimeRuleMapper ruleMapper;
    private final AuthzPolicyRuntimeSubjectMapper subjectMapper;
    private final AuthzPolicyRuntimeResourceMapper runtimeResourceMapper;
    private final AuthzGrantMapper grantMapper;
    private final AuthzGrantScopeMapper grantScopeMapper;
    private final AuthzDecisionLogMapper decisionLogMapper;
    private final PolicyConditionEvaluator conditionEvaluator;
    private final AuthorizationObligationMerger obligationMerger;
    private final AuthorizationJsonCodec jsonCodec;
    private final AuthorizationCatalog catalog;
    private final AdministratorAuthorizationPolicy administratorPolicy;
    private final RuntimePolicyRuleMatcher runtimeRuleMatcher;
    private final SubjectSelectorMatcher subjectMatcher;

    @Override
    public AccessDecision decide(AccessRequest request) {
        long start = System.currentTimeMillis();
        SubjectContext subject = request == null || request.getSubject() == null
            ? subjectProvider.currentSubject() : request.getSubject();
        // 将本次决策实际使用的可信主体快照保留给后续行条件和凭据解析，禁止执行阶段另取主体。
        if (request != null && request.getSubject() == null) {
            request.setSubject(subject);
        }
        if (request == null || StringUtils.isBlank(request.getAction())
            || (request.getResourceId() == null) == (request.getResource() == null)) {
            return complete(request, subject, null, deny("AUTHZ_REQUEST_INVALID", "资源和动作不能为空"), start);
        }
        ResourceDescriptor resource = request.getResource() == null
            ? resourceRegistry.findById(request.getResourceId()).orElse(null) : request.getResource();
        if (resource == null) {
            return complete(request, subject, null, deny("AUTHZ_RESOURCE_NOT_FOUND", "授权资源不存在"), start);
        }
        if (subject == null || !Objects.equals(subject.getTenantId(), resource.getTenantId())) {
            return complete(request, subject, resource, deny("AUTHZ_TENANT_MISMATCH", "禁止跨租户访问资源"), start);
        }
        if (!ACTIVE.equals(resource.getStatus())) {
            return complete(request, subject, resource, deny("AUTHZ_RESOURCE_DISABLED", "授权资源已停用"), start);
        }
        if (StringUtils.isNotBlank(request.getResourceType())
            && !request.getResourceType().equals(resource.getResourceType())) {
            return complete(request, subject, resource, deny("AUTHZ_RESOURCE_TYPE_MISMATCH", "资源类型不匹配"), start);
        }

        String requestPurpose = purpose(request);
        if (StringUtils.isNotBlank(requestPurpose) && !catalog.supportsPurpose(resource.getResourceType(), requestPurpose)) {
            return complete(request, subject, resource,
                deny("AUTHZ_PURPOSE_NOT_APPLICABLE", "访问目的不适用于当前资源类型"), start);
        }

        if (administratorPolicy.permits(subject, request.getAction())) {
            return complete(request, subject, resource, AccessDecision.builder()
                .decision(DecisionType.PERMIT).reasonCode("AUTHZ_ADMIN_BYPASS")
                .reason("管理员管理动作直通").obligation(AuthorizationObligation.builder().build()).build(), start);
        }

        // 方案C：若请求未指定用途，则自动推导该资源类型支持的所有用途，依次评估
        if (StringUtils.isBlank(requestPurpose)) {
            var supportedPurposes = catalog.purposesFor(resource.getResourceType());
            if (!supportedPurposes.isEmpty()) {
                AccessDecision lastDenyDecision = null;
                for (var purposeDef : supportedPurposes) {
                    AccessRequest subRequest = AccessRequest.builder()
                        .resource(resource)
                        .resourceId(resource.getResourceId())
                        .resourceType(resource.getResourceType())
                        .action(request.getAction())
                        .subject(subject)
                        .environment(EnvironmentContext.builder()
                            .purpose(purposeDef.code())
                            .requestTime(request.getEnvironment() != null && request.getEnvironment().getRequestTime() != null
                                ? request.getEnvironment().getRequestTime() : LocalDateTime.now())
                            .ip(request.getEnvironment() != null ? request.getEnvironment().getIp() : null)
                            .attributes(request.getEnvironment() != null ? request.getEnvironment().getAttributes() : Map.of())
                            .build())
                        .build();
                    AccessDecision subDecision = decide(subRequest);
                    if (subDecision.isAllowed()) {
                        return complete(request, subject, resource, AccessDecision.builder()
                            .decision(DecisionType.PERMIT)
                            .reasonCode(subDecision.getReasonCode())
                            .reason("在用途【" + purposeDef.label() + "】下授权通过")
                            .matchedPolicyIds(subDecision.getMatchedPolicyIds())
                            .matchedGrantIds(subDecision.getMatchedGrantIds())
                            .obligation(subDecision.getObligation())
                            .build(), start);
                    }
                    lastDenyDecision = subDecision;
                }
                return complete(request, subject, resource, lastDenyDecision != null ? lastDenyDecision
                    : deny("AUTHZ_DEFAULT_DENY", "未命中任何合法用途下的允许策略或有效授权"), start);
            }
        }

        Set<Long> lineage = lineage(resource);
        List<AuthzPolicyRuntimeRule> candidates = ruleMapper.selectList(
            new LambdaQueryWrapper<AuthzPolicyRuntimeRule>()
                .eq(AuthzPolicyRuntimeRule::getResourceType, resource.getResourceType())
                .orderByDesc(AuthzPolicyRuntimeRule::getPriority));
        Set<Long> groupIds = candidates.stream().map(AuthzPolicyRuntimeRule::getPolicyGroupId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<AuthzPolicyRuntimeSubject>> subjects = groupIds.isEmpty() ? Map.of()
            : subjectMapper.selectList(new LambdaQueryWrapper<AuthzPolicyRuntimeSubject>()
                .in(AuthzPolicyRuntimeSubject::getPolicyGroupId, groupIds)).stream()
                .collect(java.util.stream.Collectors.groupingBy(AuthzPolicyRuntimeSubject::getPolicyGroupId));
        Set<Long> ruleIds = candidates.stream().map(AuthzPolicyRuntimeRule::getRuntimeRuleId)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, List<AuthzPolicyRuntimeResource>> resources = ruleIds.isEmpty() ? Map.of()
            : runtimeResourceMapper.selectList(new LambdaQueryWrapper<AuthzPolicyRuntimeResource>()
                .in(AuthzPolicyRuntimeResource::getRuntimeRuleId, ruleIds)).stream()
                .collect(java.util.stream.Collectors.groupingBy(AuthzPolicyRuntimeResource::getRuntimeRuleId));

        List<Long> matchedVersions = new ArrayList<>();
        List<AuthorizationObligation> permitObligations = new ArrayList<>();
        boolean permit = false;
        for (AuthzPolicyRuntimeRule rule : candidates) {
            if (!runtimeRuleMatcher.matches(rule, subjects.get(rule.getPolicyGroupId()), subject,
                request.getAction(), purpose(request))
                || !runtimeRuleMatcher.matchesResource(
                    rule, resources.get(rule.getRuntimeRuleId()), resource.getResourceId(), lineage)
                || !matchesCondition(rule.getConditionJson(), subject, resource, request.getEnvironment())) {
                continue;
            }
            matchedVersions.add(rule.getPolicyGroupVersionId());
            if (DecisionType.DENY.code().equals(rule.getEffect())) {
                return complete(request, subject, resource, AccessDecision.builder()
                    .decision(DecisionType.DENY).reasonCode("AUTHZ_EXPLICIT_DENY")
                    .reason("命中显式拒绝策略").matchedPolicyIds(List.copyOf(matchedVersions))
                    .obligation(AuthorizationObligation.builder().build()).build(), start);
            }
            permit = true;
            if (StringUtils.isNotBlank(rule.getObligationJson())) {
                permitObligations.add(jsonCodec.read(rule.getObligationJson(), AuthorizationObligation.class));
            }
        }
        GrantMatch grant = matchesGrant(subject, resource, lineage, request);
        if (!permit && grant.grantIds().isEmpty()) {
            return complete(request, subject, resource, deny("AUTHZ_DEFAULT_DENY", "没有匹配的允许策略或有效授权"), start);
        }
        permitObligations.addAll(grant.obligations());
        return complete(request, subject, resource, AccessDecision.builder()
            .decision(DecisionType.PERMIT)
            .reasonCode(permit && !grant.grantIds().isEmpty() ? "AUTHZ_POLICY_AND_GRANT_PERMIT"
                : permit ? "AUTHZ_POLICY_PERMIT" : "AUTHZ_GRANT_PERMIT")
            .reason("授权通过")
            .matchedPolicyIds(matchedVersions.stream().distinct().toList())
            .matchedGrantIds(grant.grantIds())
            .obligation(obligationMerger.merge(permitObligations)).build(), start);
    }

    @Override
    public void check(AccessRequest request) {
        AccessDecision decision = decide(request);
        if (!decision.isAllowed()) {
            throw new ServiceException(AuthorizationErrorCode.ACCESS_DENIED, decision.getReason());
        }
    }

    private GrantMatch matchesGrant(SubjectContext subject, ResourceDescriptor resource,
                                    Set<Long> lineage, AccessRequest request) {
        LocalDateTime now = LocalDateTime.now();
        List<AuthzGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<AuthzGrant>()
                .eq(AuthzGrant::getStatus, GrantStatus.ACTIVE.code())
                .le(AuthzGrant::getEffectiveTime, now)
                .and(value -> value.isNull(AuthzGrant::getExpireTime).or().ge(AuthzGrant::getExpireTime, now)))
            .stream().filter(value -> subjectMatcher.matches(
                value.getGranteeType(), value.getGranteeId(), false, subject))
            .filter(value -> purpose(request).equals(value.getPurpose())).toList();
        if (grants.isEmpty()) {
            return GrantMatch.empty();
        }
        Set<Long> ids = grants.stream().map(AuthzGrant::getGrantId).collect(java.util.stream.Collectors.toSet());
        Map<Long, List<AuthzGrantScope>> scopes = grantScopeMapper.selectList(
                new LambdaQueryWrapper<AuthzGrantScope>().in(AuthzGrantScope::getGrantId, ids)
                    .eq(AuthzGrantScope::getActionCode, request.getAction()))
            .stream().collect(java.util.stream.Collectors.groupingBy(AuthzGrantScope::getGrantId));
        List<Long> matched = new ArrayList<>();
        List<AuthorizationObligation> obligations = new ArrayList<>();
        for (AuthzGrant grant : grants) {
            if (!matchesCondition(grant.getConditionJson(), subject, resource, request.getEnvironment())) {
                continue;
            }
            for (AuthzGrantScope scope : safe(scopes.get(grant.getGrantId()))) {
                if (lineage.contains(scope.getResourceId())
                    && (Objects.equals(scope.getResourceId(), resource.getResourceId())
                    || Boolean.TRUE.equals(scope.getIncludeChildren()))) {
                    matched.add(grant.getGrantId());
                    if (StringUtils.isNotBlank(scope.getObligationJson())) {
                        obligations.add(jsonCodec.read(scope.getObligationJson(), AuthorizationObligation.class));
                    }
                    break;
                }
            }
        }
        return new GrantMatch(matched.stream().distinct().toList(), obligations);
    }

    private boolean matchesCondition(String json, SubjectContext subject, ResourceDescriptor resource,
                                     EnvironmentContext environment) {
        if (StringUtils.isBlank(json)) {
            return true;
        }
        try {
            return conditionEvaluator.matches(jsonCodec.read(json, PolicyConditionGroup.class),
                subject, resource, environment);
        } catch (RuntimeException exception) {
            log.warn("授权条件解析失败，按不匹配处理", exception);
            return false;
        }
    }

    private Set<Long> lineage(ResourceDescriptor resource) {
        Set<Long> result = new LinkedHashSet<>();
        if (resource.getResourceId() == null) {
            return result;
        }
        Long currentId = resource.getResourceId();
        int depth = 0;
        while (currentId != null && depth++ < MAX_RESOURCE_DEPTH && result.add(currentId)) {
            AuthzResource current = resourceMapper.selectById(currentId);
            currentId = current == null || !Boolean.TRUE.equals(current.getInheritPolicy())
                ? null : current.getParentId();
        }
        return result;
    }

    private String purpose(AccessRequest request) {
        return request == null || request.getEnvironment() == null
            ? null : request.getEnvironment().getPurpose();
    }

    private AccessDecision deny(String code, String reason) {
        return AccessDecision.builder().decision(DecisionType.DENY).reasonCode(code).reason(reason)
            .obligation(AuthorizationObligation.builder().build()).build();
    }

    private AccessDecision complete(AccessRequest request, SubjectContext subject,
                                    ResourceDescriptor resource, AccessDecision decision, long start) {
        try {
            AuthzDecisionLog audit = new AuthzDecisionLog();
            String tenantId = resource == null ? subject == null ? null : subject.getTenantId() : resource.getTenantId();
            audit.setDecisionId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            audit.setTenantId(tenantId);
            audit.setSubjectTenantId(subject == null ? tenantId : subject.getTenantId());
            PrincipalType type = subject == null || subject.getPrincipalType() == null
                ? PrincipalType.HUMAN : subject.getPrincipalType();
            audit.setSubjectType(type.name());
            audit.setSubjectId(subject == null ? "ANONYMOUS" : StringUtils.defaultIfBlank(subject.getPrincipalId(),
                Objects.toString(subject.getUserId(), "ANONYMOUS")));
            audit.setSubjectSnapshotJson(jsonCodec.write(Map.of(
                "userId", subject == null ? "" : Objects.toString(subject.getUserId(), ""),
                "deptId", subject == null ? "" : Objects.toString(subject.getDeptId(), ""))));
            audit.setResourceTenantId(tenantId);
            audit.setResourceId(resource == null ? null : resource.getResourceId());
            audit.setResourceTypeSnapshot(resource == null ? Objects.toString(request == null ? null
                : request.getResourceType(), "UNKNOWN") : resource.getResourceType());
            audit.setResourceCodeSnapshot(resource == null ? "UNKNOWN" : resource.getResourceCode());
            audit.setActionCode(request == null ? "UNKNOWN" : Objects.toString(request.getAction(), "UNKNOWN"));
            audit.setDecision(decision.getDecision().name());
            audit.setReasonCode(decision.getReasonCode());
            audit.setPolicyVersionIdsJson(jsonCodec.write(decision.getMatchedPolicyIds()));
            audit.setGrantIdsJson(jsonCodec.write(decision.getMatchedGrantIds()));
            audit.setObligationSummaryJson(jsonCodec.write(decision.getObligation()));
            audit.setPurpose(purpose(request));
            String traceId = request == null || request.getEnvironment() == null
                ? MDC.get("traceId") : request.getEnvironment().getTraceId();
            audit.setTraceId(StringUtils.defaultIfBlank(traceId,
                java.util.UUID.randomUUID().toString().replace("-", "")));
            audit.setDurationMs(System.currentTimeMillis() - start);
            audit.setOccurredTime(LocalDateTime.now());
            decisionLogMapper.insert(audit);
        } catch (RuntimeException exception) {
            log.error("写入授权决策审计失败，decision={}", decision.getReasonCode(), exception);
            if (decision.isAllowed() && isHighRisk(request, resource)) {
                return deny("AUTHZ_AUDIT_UNAVAILABLE", "高风险操作的授权审计不可用，已拒绝执行");
            }
        }
        return decision;
    }

    /** 高风险允许决策必须成功留痕；低风险读操作允许在审计故障时降级并告警。 */
    private boolean isHighRisk(AccessRequest request, ResourceDescriptor resource) {
        if (request == null || StringUtils.isBlank(request.getAction())) {
            return false;
        }
        String resourceType = resource == null ? request.getResourceType() : resource.getResourceType();
        return catalog.findResourceType(resourceType).stream()
            .flatMap(type -> type.actions().stream())
            .filter(action -> request.getAction().equals(action.code()))
            .map(action -> StringUtils.defaultString(action.riskLevel()).toUpperCase(java.util.Locale.ROOT))
            .anyMatch(risk -> "HIGH".equals(risk) || "CRITICAL".equals(risk));
    }

    private <T> Collection<T> safe(Collection<T> values) {
        return values == null ? List.of() : values;
    }

    private record GrantMatch(List<Long> grantIds, List<AuthorizationObligation> obligations) {
        private static GrantMatch empty() {
            return new GrantMatch(List.of(), List.of());
        }
    }
}

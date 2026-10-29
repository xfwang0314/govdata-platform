package com.xfwang.platform.auth.application.decision;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.enums.GrantStatus;
import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeResource;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeRule;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeSubject;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.domain.entity.AuthzGrant;
import com.xfwang.platform.auth.domain.entity.AuthzGrantScope;
import com.xfwang.platform.auth.domain.policy.PolicyConditionEvaluator;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import com.xfwang.platform.auth.domain.service.AdministratorAuthorizationPolicy;
import com.xfwang.platform.auth.domain.service.SubjectSelectorMatcher;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeResourceMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 一次加载运行时索引并在内存解析列表资源范围，禁止逐资源数据库鉴权。 */
@Service
@RequiredArgsConstructor
public class DefaultAuthorizedResourceService implements AuthorizedResourceService {

    private static final String ACTIVE = "ACTIVE";
    private static final int MAX_DEPTH = 32;

    private final SubjectProvider subjectProvider;
    private final AdministratorAuthorizationPolicy administratorPolicy;
    private final AuthzResourceMapper resourceMapper;
    private final AuthzPolicyRuntimeRuleMapper ruleMapper;
    private final AuthzPolicyRuntimeSubjectMapper subjectMapper;
    private final AuthzPolicyRuntimeResourceMapper runtimeResourceMapper;
    private final PolicyConditionEvaluator conditionEvaluator;
    private final AuthorizationJsonCodec jsonCodec;
    private final RuntimePolicyRuleMatcher runtimeRuleMatcher;
    private final SubjectSelectorMatcher subjectMatcher;
    private final AuthzGrantMapper grantMapper;
    private final AuthzGrantScopeMapper grantScopeMapper;

    @Override
    public AuthorizedResourceScope resolveScope(AuthorizedResourceQuery query) {
        if (query == null || StringUtils.isAnyBlank(query.getResourceType(), query.getAction(), query.getPurpose())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "资源类型、动作和访问目的不能为空");
        }
        SubjectContext subject = subjectProvider.currentSubject();
        if (administratorPolicy.permits(subject, query.getAction())) {
            return AuthorizedResourceScope.unrestrictedScope();
        }
        List<AuthzResource> targets = resourceMapper.selectList(new LambdaQueryWrapper<AuthzResource>()
            .eq(AuthzResource::getResourceType, query.getResourceType())
            .eq(AuthzResource::getStatus, ACTIVE));
        if (targets.isEmpty()) {
            return AuthorizedResourceScope.restricted(Set.of());
        }
        List<AuthzPolicyRuntimeRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<AuthzPolicyRuntimeRule>()
                .eq(AuthzPolicyRuntimeRule::getResourceType, query.getResourceType()));
        Set<Long> groupIds = rules.stream().map(AuthzPolicyRuntimeRule::getPolicyGroupId).collect(Collectors.toSet());
        Map<Long, List<AuthzPolicyRuntimeSubject>> subjects = groupIds.isEmpty() ? Map.of()
            : subjectMapper.selectList(new LambdaQueryWrapper<AuthzPolicyRuntimeSubject>()
                .in(AuthzPolicyRuntimeSubject::getPolicyGroupId, groupIds)).stream()
                .collect(Collectors.groupingBy(AuthzPolicyRuntimeSubject::getPolicyGroupId));
        Set<Long> ruleIds = rules.stream().map(AuthzPolicyRuntimeRule::getRuntimeRuleId).collect(Collectors.toSet());
        Map<Long, List<AuthzPolicyRuntimeResource>> resources = ruleIds.isEmpty() ? Map.of()
            : runtimeResourceMapper.selectList(new LambdaQueryWrapper<AuthzPolicyRuntimeResource>()
                .in(AuthzPolicyRuntimeResource::getRuntimeRuleId, ruleIds)).stream()
                .collect(Collectors.groupingBy(AuthzPolicyRuntimeResource::getRuntimeRuleId));
        LocalDateTime now = LocalDateTime.now();
        List<AuthzGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<AuthzGrant>()
                .eq(AuthzGrant::getStatus, GrantStatus.ACTIVE.code())
                .le(AuthzGrant::getEffectiveTime, now)
                .and(value -> value.isNull(AuthzGrant::getExpireTime).or().ge(AuthzGrant::getExpireTime, now)))
            .stream()
            .filter(grant -> subjectMatcher.matches(
                grant.getGranteeType(), grant.getGranteeId(), false, subject))
            .filter(grant -> Objects.equals(query.getPurpose(), grant.getPurpose()))
            .toList();
        Set<Long> grantIds = grants.stream().map(AuthzGrant::getGrantId).collect(Collectors.toSet());
        Map<Long, List<AuthzGrantScope>> grantScopes = grantIds.isEmpty() ? Map.of()
            : grantScopeMapper.selectList(new LambdaQueryWrapper<AuthzGrantScope>()
                .in(AuthzGrantScope::getGrantId, grantIds)
                .eq(AuthzGrantScope::getActionCode, query.getAction())).stream()
                .collect(Collectors.groupingBy(AuthzGrantScope::getGrantId));
        Map<Long, AuthzResource> index = loadResourceIndex(targets);
        EnvironmentContext environment = EnvironmentContext.builder()
            .purpose(query.getPurpose()).requestTime(LocalDateTime.now()).build();
        Set<Long> allowed = new LinkedHashSet<>();
        for (AuthzResource target : targets) {
            ResourceDescriptor resource = descriptor(target);
            Set<Long> lineage = lineage(target, index);
            boolean permit = false;
            boolean deny = false;
            for (AuthzPolicyRuntimeRule rule : rules) {
                if (!runtimeRuleMatcher.matches(rule, subjects.get(rule.getPolicyGroupId()), subject,
                    query.getAction(), query.getPurpose())
                    || !runtimeRuleMatcher.matchesResource(
                        rule, resources.get(rule.getRuntimeRuleId()), target.getResourceId(), lineage)
                    || !matchesCondition(rule.getConditionJson(), subject, resource, environment)) {
                    continue;
                }
                if (DecisionType.DENY.code().equals(rule.getEffect())) {
                    deny = true;
                    break;
                }
                permit = true;
            }
            boolean grantPermit = matchesGrant(grants, grantScopes, subject, resource, lineage, environment);
            if ((permit || grantPermit) && !deny) {
                allowed.add(target.getResourceId());
            }
        }
        return AuthorizedResourceScope.restricted(allowed);
    }

    private boolean matchesCondition(String json, SubjectContext subject,
                                     ResourceDescriptor resource, EnvironmentContext environment) {
        if (StringUtils.isBlank(json)) {
            return true;
        }
        try {
            return conditionEvaluator.matches(jsonCodec.read(json, PolicyConditionGroup.class),
                subject, resource, environment);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesGrant(List<AuthzGrant> grants,
                                 Map<Long, List<AuthzGrantScope>> grantScopes,
                                 SubjectContext subject, ResourceDescriptor resource,
                                 Set<Long> lineage, EnvironmentContext environment) {
        for (AuthzGrant grant : grants) {
            if (!matchesCondition(grant.getConditionJson(), subject, resource, environment)) {
                continue;
            }
            for (AuthzGrantScope scope : safe(grantScopes.get(grant.getGrantId()))) {
                if (lineage.contains(scope.getResourceId())
                    && (Objects.equals(scope.getResourceId(), resource.getResourceId())
                    || Boolean.TRUE.equals(scope.getIncludeChildren()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<Long, AuthzResource> loadResourceIndex(List<AuthzResource> targets) {
        Map<Long, AuthzResource> result = targets.stream().collect(Collectors.toMap(
            AuthzResource::getResourceId, value -> value, (left, right) -> left, LinkedHashMap::new));
        Set<Long> frontier = targets.stream().map(AuthzResource::getParentId).filter(Objects::nonNull)
            .filter(id -> !result.containsKey(id)).collect(Collectors.toSet());
        int depth = 0;
        while (!frontier.isEmpty() && depth++ < MAX_DEPTH) {
            List<AuthzResource> parents = resourceMapper.selectBatchIds(frontier);
            parents.forEach(value -> result.putIfAbsent(value.getResourceId(), value));
            frontier = parents.stream().map(AuthzResource::getParentId).filter(Objects::nonNull)
                .filter(id -> !result.containsKey(id)).collect(Collectors.toSet());
        }
        return result;
    }

    private Set<Long> lineage(AuthzResource target, Map<Long, AuthzResource> index) {
        Set<Long> result = new LinkedHashSet<>();
        AuthzResource current = target;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH && result.add(current.getResourceId())) {
            current = Boolean.TRUE.equals(current.getInheritPolicy()) ? index.get(current.getParentId()) : null;
        }
        return result;
    }

    private ResourceDescriptor descriptor(AuthzResource value) {
        Map<String, Object> attributes = StringUtils.isBlank(value.getAttributesJson()) ? Map.of()
            : jsonCodec.read(value.getAttributesJson(), new TypeReference<Map<String, Object>>() { });
        Set<Long> deptPath = StringUtils.isBlank(value.getOwnerDeptPathJson()) ? Set.of()
            : jsonCodec.read(value.getOwnerDeptPathJson(), new TypeReference<Set<Long>>() { });
        return ResourceDescriptor.builder().resourceId(value.getResourceId()).tenantId(value.getTenantId())
            .resourceCode(value.getResourceCode()).resourceName(value.getResourceName())
            .resourceType(value.getResourceType()).parentId(value.getParentId()).businessId(value.getBusinessId())
            .sourceModule(value.getSourceModule()).ownerDeptId(value.getOwnerDeptId())
            .ownerUserId(value.getOwnerUserId()).ownerDeptPathIds(deptPath).regionCode(value.getRegionCode())
            .classification(value.getClassification()).securityLevel(value.getSecurityLevel())
            .status(value.getStatus()).attributes(attributes).build();
    }

    private <T> Collection<T> safe(Collection<T> values) {
        return values == null ? List.of() : values;
    }
}

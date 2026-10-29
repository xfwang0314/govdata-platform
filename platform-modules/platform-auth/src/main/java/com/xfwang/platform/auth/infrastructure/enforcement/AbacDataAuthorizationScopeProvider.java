package com.xfwang.platform.auth.infrastructure.enforcement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.enums.DepartmentDataScopePreset;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.decision.RuntimePolicyRuleMatcher;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeRule;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeSubject;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import com.xfwang.platform.auth.domain.service.AdministratorAuthorizationPolicy;
import com.xfwang.platform.auth.domain.service.DepartmentDataScopeConditionResolver;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.helper.AbacDataScopeHelper;
import org.dromara.system.api.DeptService;
import org.dromara.system.api.permission.DataAuthorizationScope;
import org.dromara.system.api.permission.DataAuthorizationScopeProvider;
import org.dromara.system.api.permission.DataAuthorizationScopeRequest;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 从当前策略组运行时索引解析结构化部门数据范围。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbacDataAuthorizationScopeProvider implements DataAuthorizationScopeProvider {

    private final SubjectProvider subjectProvider;
    private final DeptService deptService;
    private final AuthzPolicyRuntimeRuleMapper ruleMapper;
    private final AuthzPolicyRuntimeSubjectMapper subjectMapper;
    private final AuthorizationJsonCodec jsonCodec;
    private final DepartmentDataScopeConditionResolver conditionResolver;
    private final AdministratorAuthorizationPolicy administratorPolicy;
    private final RuntimePolicyRuleMatcher runtimeRuleMatcher;

    @Override
    public Optional<DataAuthorizationScope> resolve(DataAuthorizationScopeRequest request) {
        if (!supported(request)) {
            return Optional.empty();
        }
        SubjectContext subject = subjectProvider.currentSubject();
        if (administratorPolicy.permits(subject, request.action())) {
            return Optional.of(DataAuthorizationScope.unrestrictedScope());
        }
        return AbacDataScopeHelper.ignore(() -> Optional.of(resolveInternal(request, subject)));
    }

    private DataAuthorizationScope resolveInternal(DataAuthorizationScopeRequest request, SubjectContext subject) {
        List<AuthzPolicyRuntimeRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<AuthzPolicyRuntimeRule>()
                .eq(AuthzPolicyRuntimeRule::getResourceType, request.resourceType())
                .eq(AuthzPolicyRuntimeRule::getResourceScope, "TYPE"));
        if (rules.isEmpty()) {
            return DataAuthorizationScope.deniedScope();
        }
        Set<Long> groupIds = rules.stream().map(AuthzPolicyRuntimeRule::getPolicyGroupId).collect(Collectors.toSet());
        Map<Long, List<AuthzPolicyRuntimeSubject>> subjects = subjectMapper.selectList(
                new LambdaQueryWrapper<AuthzPolicyRuntimeSubject>()
                    .in(AuthzPolicyRuntimeSubject::getPolicyGroupId, groupIds))
            .stream().collect(Collectors.groupingBy(AuthzPolicyRuntimeSubject::getPolicyGroupId));
        List<DataAuthorizationScope> permits = new ArrayList<>();
        for (AuthzPolicyRuntimeRule rule : rules) {
            if (!runtimeRuleMatcher.matches(rule, subjects.get(rule.getPolicyGroupId()), subject,
                request.action(), request.purpose())) {
                continue;
            }
            if (DecisionType.DENY.code().equals(rule.getEffect())) {
                return DataAuthorizationScope.deniedScope();
            }
            resolvePermit(rule, subject, request.resourceType()).ifPresent(permits::add);
        }
        return merge(permits);
    }

    private Optional<DataAuthorizationScope> resolvePermit(AuthzPolicyRuntimeRule rule,
                                                            SubjectContext subject, String resourceType) {
        try {
            PolicyConditionGroup condition = jsonCodec.read(rule.getConditionJson(), PolicyConditionGroup.class);
            return conditionResolver.resolve(condition).flatMap(value -> toScope(value, subject, resourceType));
        } catch (RuntimeException exception) {
            log.warn("运行时规则 {} 不能转换为结构化数据范围，按不匹配处理", rule.getRuntimeRuleId(), exception);
            return Optional.empty();
        }
    }

    private Optional<DataAuthorizationScope> toScope(
            DepartmentDataScopeConditionResolver.ResolvedDepartmentScope resolved,
            SubjectContext subject, String resourceType) {
        DepartmentDataScopePreset preset = resolved.preset();
        if (SystemAuthorizationResources.DEPARTMENT.equals(resourceType)
            && (preset == DepartmentDataScopePreset.SELF
            || preset == DepartmentDataScopePreset.DEPARTMENT_AND_DESCENDANTS_OR_SELF)) {
            return Optional.empty();
        }
        return switch (preset) {
            case ALL -> Optional.of(DataAuthorizationScope.unrestrictedScope());
            case CUSTOM -> Optional.of(DataAuthorizationScope.restricted(resolved.customDepartmentIds(), false));
            case DEPARTMENT -> Optional.of(DataAuthorizationScope.restricted(
                subject.getDeptId() == null ? Set.of() : Set.of(subject.getDeptId()), false));
            case DEPARTMENT_AND_DESCENDANTS -> Optional.of(DataAuthorizationScope.restricted(
                deptService.selectDeptAndChildIds(subject.getDeptId()), false));
            case SELF -> Optional.of(DataAuthorizationScope.restricted(Set.of(), true));
            case DEPARTMENT_AND_DESCENDANTS_OR_SELF -> Optional.of(DataAuthorizationScope.restricted(
                deptService.selectDeptAndChildIds(subject.getDeptId()), true));
        };
    }

    private DataAuthorizationScope merge(Collection<DataAuthorizationScope> scopes) {
        if (scopes.stream().anyMatch(DataAuthorizationScope::unrestricted)) {
            return DataAuthorizationScope.unrestrictedScope();
        }
        LinkedHashSet<Long> departments = new LinkedHashSet<>();
        boolean self = false;
        for (DataAuthorizationScope scope : scopes) {
            departments.addAll(scope.departmentIds());
            self |= scope.includeCurrentUser();
        }
        return DataAuthorizationScope.restricted(departments, self);
    }

    private boolean supported(DataAuthorizationScopeRequest request) {
        return request != null
            && request.resourceType() != null && !request.resourceType().isBlank()
            && request.action() != null && !request.action().isBlank()
            && request.purpose() != null && !request.purpose().isBlank();
    }

}

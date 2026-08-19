package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.enums.DepartmentDataScopePreset;
import com.xfwang.platform.auth.domain.policy.model.PolicyCondition;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 将受控部门范围条件反向解析为可执行的结构化计划。 */
@Component
public class DepartmentDataScopeConditionResolver {

    public Optional<ResolvedDepartmentScope> resolve(PolicyConditionGroup group) {
        if (group == null || !safe(group.getGroups()).isEmpty()) {
            return Optional.empty();
        }
        List<PolicyCondition> conditions = safe(group.getConditions());
        String logic = normalize(group.getLogic());
        if ("ALL".equals(logic) && conditions.isEmpty()) {
            return Optional.of(new ResolvedDepartmentScope(DepartmentDataScopePreset.ALL, Set.of()));
        }
        if ("ALL".equals(logic) && conditions.size() == 1) {
            PolicyCondition condition = conditions.get(0);
            if (constantIn(condition)) {
                Set<Long> departmentIds = longValues(condition.getValue());
                return departmentIds.isEmpty() ? Optional.empty()
                    : Optional.of(new ResolvedDepartmentScope(DepartmentDataScopePreset.CUSTOM, departmentIds));
            }
            if (attributeComparison(condition, "resource.ownerDeptId", ConditionOperator.EQ, "subject.deptId")) {
                return preset(DepartmentDataScopePreset.DEPARTMENT);
            }
            if (attributeComparison(condition, "resource.ownerDeptPathIds", ConditionOperator.CONTAINS,
                "subject.deptId")) {
                return preset(DepartmentDataScopePreset.DEPARTMENT_AND_DESCENDANTS);
            }
            if (attributeComparison(condition, "resource.ownerUserId", ConditionOperator.EQ, "subject.userId")) {
                return preset(DepartmentDataScopePreset.SELF);
            }
        }
        if ("ANY".equals(logic) && conditions.size() == 2
            && conditions.stream().anyMatch(value -> attributeComparison(value,
            "resource.ownerDeptPathIds", ConditionOperator.CONTAINS, "subject.deptId"))
            && conditions.stream().anyMatch(value -> attributeComparison(value,
            "resource.ownerUserId", ConditionOperator.EQ, "subject.userId"))) {
            return preset(DepartmentDataScopePreset.DEPARTMENT_AND_DESCENDANTS_OR_SELF);
        }
        return Optional.empty();
    }

    private Optional<ResolvedDepartmentScope> preset(DepartmentDataScopePreset preset) {
        return Optional.of(new ResolvedDepartmentScope(preset, Set.of()));
    }

    private boolean constantIn(PolicyCondition condition) {
        return condition != null
            && "resource.ownerDeptId".equals(condition.getLeft())
            && condition.getOperator() == ConditionOperator.IN
            && condition.getRightAttribute() == null
            && condition.getValue() instanceof Collection<?>;
    }

    private boolean attributeComparison(PolicyCondition condition, String left,
                                        ConditionOperator operator, String right) {
        return condition != null
            && left.equals(condition.getLeft())
            && operator == condition.getOperator()
            && right.equals(condition.getRightAttribute())
            && condition.getValue() == null;
    }

    private Set<Long> longValues(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        try {
            for (Object item : collection) {
                if (item != null) {
                    result.add(Long.valueOf(String.valueOf(item)));
                }
            }
            return Set.copyOf(result);
        } catch (NumberFormatException exception) {
            return Set.of();
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record ResolvedDepartmentScope(DepartmentDataScopePreset preset, Set<Long> customDepartmentIds) {
        public ResolvedDepartmentScope {
            customDepartmentIds = customDepartmentIds == null ? Set.of() : Set.copyOf(customDepartmentIds);
        }
    }
}

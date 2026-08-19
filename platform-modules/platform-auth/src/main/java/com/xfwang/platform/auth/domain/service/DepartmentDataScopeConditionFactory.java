package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.enums.DepartmentDataScopePreset;
import com.xfwang.platform.auth.domain.policy.model.PolicyCondition;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** 六种部门数据范围到受限 ABAC 条件 DSL 的唯一转换器。 */
@Component
public class DepartmentDataScopeConditionFactory {

    public PolicyConditionGroup create(DepartmentDataScopePreset preset, Collection<Long> customDeptIds) {
        return switch (preset) {
            case ALL -> group("ALL", List.of());
            case CUSTOM -> custom(customDeptIds);
            case DEPARTMENT -> group("ALL", List.of(compare(
                "resource.ownerDeptId", ConditionOperator.EQ, "subject.deptId")));
            case DEPARTMENT_AND_DESCENDANTS -> group("ALL", List.of(compare(
                "resource.ownerDeptPathIds", ConditionOperator.CONTAINS, "subject.deptId")));
            case SELF -> group("ALL", List.of(compare(
                "resource.ownerUserId", ConditionOperator.EQ, "subject.userId")));
            case DEPARTMENT_AND_DESCENDANTS_OR_SELF -> group("ANY", List.of(
                compare("resource.ownerDeptPathIds", ConditionOperator.CONTAINS, "subject.deptId"),
                compare("resource.ownerUserId", ConditionOperator.EQ, "subject.userId")));
        };
    }

    private PolicyConditionGroup custom(Collection<Long> customDeptIds) {
        LinkedHashSet<Long> values = new LinkedHashSet<>(customDeptIds == null ? List.of() : customDeptIds);
        values.remove(null);
        if (values.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "自定义数据权限必须选择部门");
        }
        PolicyCondition condition = new PolicyCondition(
            "resource.ownerDeptId", ConditionOperator.IN, null, List.copyOf(values));
        return group("ALL", List.of(condition));
    }

    private PolicyCondition compare(String left, ConditionOperator operator, String right) {
        return new PolicyCondition(left, operator, right, null);
    }

    private PolicyConditionGroup group(String logic, List<PolicyCondition> conditions) {
        return new PolicyConditionGroup(logic, new ArrayList<>(conditions), new ArrayList<>());
    }
}

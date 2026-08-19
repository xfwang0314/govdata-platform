package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.enums.DepartmentDataScopePreset;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartmentDataScopeConditionFactoryTest {

    private final DepartmentDataScopeConditionFactory factory =
        new DepartmentDataScopeConditionFactory();

    @Test
    void convertsAllSixPresetsToRestrictedAttributeConditions() {
        assertThat(factory.create(DepartmentDataScopePreset.ALL, List.of()).getConditions()).isEmpty();

        PolicyConditionGroup custom = factory.create(DepartmentDataScopePreset.CUSTOM, List.of(103L, 104L));
        assertThat(custom.getConditions().get(0).getLeft()).isEqualTo("resource.ownerDeptId");
        assertThat(custom.getConditions().get(0).getOperator()).isEqualTo(ConditionOperator.IN);

        PolicyConditionGroup department = factory.create(DepartmentDataScopePreset.DEPARTMENT, List.of());
        assertThat(department.getConditions().get(0).getRightAttribute()).isEqualTo("subject.deptId");

        PolicyConditionGroup descendants = factory.create(
            DepartmentDataScopePreset.DEPARTMENT_AND_DESCENDANTS, List.of());
        assertThat(descendants.getConditions().get(0).getLeft())
            .isEqualTo("resource.ownerDeptPathIds");
        assertThat(descendants.getConditions().get(0).getOperator())
            .isEqualTo(ConditionOperator.CONTAINS);

        PolicyConditionGroup self = factory.create(DepartmentDataScopePreset.SELF, List.of());
        assertThat(self.getConditions().get(0).getLeft()).isEqualTo("resource.ownerUserId");

        PolicyConditionGroup combined = factory.create(
            DepartmentDataScopePreset.DEPARTMENT_AND_DESCENDANTS_OR_SELF, List.of());
        assertThat(combined.getLogic()).isEqualTo("ANY");
        assertThat(combined.getConditions()).hasSize(2);
    }

    @Test
    void rejectsEmptyCustomDepartmentScope() {
        assertThatThrownBy(() -> factory.create(DepartmentDataScopePreset.CUSTOM, List.of()))
            .hasMessageContaining("必须选择部门");
    }
}

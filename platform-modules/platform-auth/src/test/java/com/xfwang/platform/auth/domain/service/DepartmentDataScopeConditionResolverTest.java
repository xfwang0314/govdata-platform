package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.enums.DepartmentDataScopePreset;
import com.xfwang.platform.auth.domain.policy.model.PolicyCondition;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentDataScopeConditionResolverTest {

    private final DepartmentDataScopeConditionFactory factory = new DepartmentDataScopeConditionFactory();
    private final DepartmentDataScopeConditionResolver resolver = new DepartmentDataScopeConditionResolver();

    @Test
    void resolvesAllControlledDepartmentScopePresets() {
        for (DepartmentDataScopePreset preset : DepartmentDataScopePreset.values()) {
            List<Long> departmentIds = preset == DepartmentDataScopePreset.CUSTOM ? List.of(101L, 102L) : List.of();
            var resolved = resolver.resolve(factory.create(preset, departmentIds));

            assertThat(resolved).isPresent();
            assertThat(resolved.orElseThrow().preset()).isEqualTo(preset);
            if (preset == DepartmentDataScopePreset.CUSTOM) {
                assertThat(resolved.orElseThrow().customDepartmentIds()).containsExactlyInAnyOrder(101L, 102L);
            }
        }
    }

    @Test
    void rejectsArbitraryPolicyCondition() {
        PolicyCondition condition = new PolicyCondition(
            "resource.securityLevel", ConditionOperator.GTE, null, 3);
        PolicyConditionGroup group = new PolicyConditionGroup("ALL", List.of(condition), List.of());

        assertThat(resolver.resolve(group)).isEmpty();
    }
}

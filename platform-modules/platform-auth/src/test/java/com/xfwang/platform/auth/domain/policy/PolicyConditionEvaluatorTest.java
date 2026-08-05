package com.xfwang.platform.auth.domain.policy;

import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.auth.domain.policy.model.PolicyCondition;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class PolicyConditionEvaluatorTest {

    private final PolicyConditionEvaluator evaluator = new PolicyConditionEvaluator();

    @Test
    void shouldCompareSubjectAndResourceAttributes() {
        SubjectContext subject = SubjectContext.builder()
            .tenantId("100001")
            .attributes(Map.of("securityClearance", 3))
            .build();
        ResourceDescriptor resource = ResourceDescriptor.builder()
            .tenantId("100001")
            .securityLevel(2)
            .build();
        PolicyCondition condition = new PolicyCondition(
            "subject.securityClearance",
            ConditionOperator.GTE,
            "resource.securityLevel",
            null
        );

        boolean result = evaluator.matches(
            new PolicyConditionGroup("ALL", List.of(condition), List.of()),
            subject,
            resource,
            EnvironmentContext.builder().requestTime(LocalDateTime.now()).build()
        );

        assertThat(result).isTrue();
    }

    @Test
    void shouldSupportAnyAndConstantCollection() {
        SubjectContext subject = SubjectContext.builder().deptId(103L).build();
        PolicyCondition wrongDept = new PolicyCondition(
            "subject.deptId", ConditionOperator.EQ, null, 102
        );
        PolicyCondition allowedDept = new PolicyCondition(
            "subject.deptId", ConditionOperator.IN, null, List.of(101, 103)
        );

        boolean result = evaluator.matches(
            new PolicyConditionGroup("ANY", List.of(wrongDept, allowedDept), List.of()),
            subject,
            ResourceDescriptor.builder().build(),
            EnvironmentContext.builder().build()
        );

        assertThat(result).isTrue();
    }

    @Test
    void shouldFailClosedWhenComparableAttributeIsMissing() {
        PolicyCondition condition = new PolicyCondition(
            "subject.securityClearance",
            ConditionOperator.LT,
            null,
            4
        );

        boolean result = evaluator.matches(
            new PolicyConditionGroup("ALL", List.of(condition), List.of()),
            SubjectContext.builder().build(),
            ResourceDescriptor.builder().build(),
            EnvironmentContext.builder().build()
        );

        assertThat(result).isFalse();
    }

    @Test
    void shouldFailClosedForEveryNegativeComparisonWhenAttributeIsMissing() {
        SubjectContext subject = SubjectContext.builder().build();
        ResourceDescriptor resource = ResourceDescriptor.builder().build();
        EnvironmentContext environment = EnvironmentContext.builder().build();

        assertThat(matches(subject, resource, environment,
            new PolicyCondition("subject.unknown", ConditionOperator.EQ,
                "resource.unknown", null))).isFalse();
        assertThat(matches(subject, resource, environment,
            new PolicyCondition("subject.unknown", ConditionOperator.NE,
                null, "enabled"))).isFalse();
        assertThat(matches(subject, resource, environment,
            new PolicyCondition("subject.unknown", ConditionOperator.NOT_IN,
                null, List.of("disabled")))).isFalse();
    }

    private boolean matches(SubjectContext subject, ResourceDescriptor resource,
                            EnvironmentContext environment, PolicyCondition condition) {
        return evaluator.matches(
            new PolicyConditionGroup("ALL", List.of(condition), List.of()),
            subject, resource, environment);
    }
}

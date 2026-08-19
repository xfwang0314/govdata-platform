package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectSelectorMatcherTest {

    private final SubjectSelectorMatcher matcher = new SubjectSelectorMatcher();

    @Test
    void mustApplyTheSameSelectorSemanticsForEveryAuthorizationEntry() {
        SubjectContext human = SubjectContext.builder()
            .principalType(PrincipalType.HUMAN)
            .principalId("100")
            .userId(100L)
            .roleIds(Set.of(10L))
            .roleKeys(Set.of("data-admin"))
            .deptId(200L)
            .deptPathIds(Set.of(1L, 20L, 200L))
            .build();

        assertThat(matcher.matches("ANY", "*", false, human)).isTrue();
        assertThat(matcher.matches("HUMAN", "100", false, human)).isTrue();
        assertThat(matcher.matches("ROLE", "10", false, human)).isTrue();
        assertThat(matcher.matches("ROLE", "data-admin", false, human)).isTrue();
        assertThat(matcher.matches("DEPARTMENT", "200", false, human)).isTrue();
        assertThat(matcher.matches("DEPARTMENT", "20", true, human)).isTrue();
        assertThat(matcher.matches("DEPARTMENT", "20", false, human)).isFalse();
    }

    @Test
    void serviceAndAgentMustMatchBothStableIdAndPrincipalType() {
        SubjectContext service = SubjectContext.builder()
            .principalType(PrincipalType.SERVICE).principalId("service-1").build();
        SubjectContext agent = SubjectContext.builder()
            .principalType(PrincipalType.AGENT).principalId("agent-1").build();

        assertThat(matcher.matches("SERVICE", "service-1", false, service)).isTrue();
        assertThat(matcher.matches("SERVICE", "service-1", false, agent)).isFalse();
        assertThat(matcher.matches("AGENT", "agent-1", false, agent)).isTrue();
        assertThat(matcher.matches("AGENT", "agent-1", false, service)).isFalse();
    }

    @Test
    void invalidSelectorMustFailClosed() {
        SubjectContext subject = SubjectContext.builder().userId(100L).build();

        assertThat(matcher.matches(null, "100", false, subject)).isFalse();
        assertThat(matcher.matches("UNKNOWN", "100", false, subject)).isFalse();
        assertThat(matcher.matches("HUMAN", "100", false, null)).isFalse();
    }
}

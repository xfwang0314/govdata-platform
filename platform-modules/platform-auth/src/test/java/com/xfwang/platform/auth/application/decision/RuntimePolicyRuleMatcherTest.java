package com.xfwang.platform.auth.application.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeRule;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeResource;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeSubject;
import com.xfwang.platform.auth.domain.service.SubjectSelectorMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePolicyRuleMatcherTest {

    private final RuntimePolicyRuleMatcher matcher = new RuntimePolicyRuleMatcher(
        new AuthorizationJsonCodec(new ObjectMapper()), new SubjectSelectorMatcher());

    @Test
    void mustMatchActionPurposeAndSubjectAsOneCandidateRule() {
        AuthzPolicyRuntimeRule rule = rule("[\"VIEW\"]", "[\"ADMINISTRATION\"]");
        AuthzPolicyRuntimeSubject selector = new AuthzPolicyRuntimeSubject();
        selector.setSubjectType("HUMAN");
        selector.setSubjectId("100");

        boolean matched = matcher.matches(rule, List.of(selector),
            SubjectContext.builder().userId(100L).build(), "VIEW", "ADMINISTRATION");

        assertThat(matched).isTrue();
        assertThat(matcher.matches(rule, List.of(selector),
            SubjectContext.builder().userId(100L).build(), "UPDATE", "ADMINISTRATION")).isFalse();
    }

    @Test
    void malformedRuntimeIndexMustFailClosed() {
        AuthzPolicyRuntimeRule rule = rule("not-json", "[\"ADMINISTRATION\"]");

        assertThat(matcher.matches(rule, List.of(),
            SubjectContext.builder().userId(100L).build(), "VIEW", "ADMINISTRATION")).isFalse();
    }

    @Test
    void resourceBindingMustUseTheSameInheritanceSemanticsForSingleAndBatchDecision() {
        AuthzPolicyRuntimeRule rule = rule("[\"VIEW\"]", "[\"ADMINISTRATION\"]");
        rule.setResourceScope("INSTANCE");
        AuthzPolicyRuntimeResource parentBinding = new AuthzPolicyRuntimeResource();
        parentBinding.setResourceId(10L);
        parentBinding.setIncludeChildren(true);

        assertThat(matcher.matchesResource(rule, List.of(parentBinding), 11L, Set.of(10L, 11L))).isTrue();
        parentBinding.setIncludeChildren(false);
        assertThat(matcher.matchesResource(rule, List.of(parentBinding), 11L, Set.of(10L, 11L))).isFalse();
    }

    private AuthzPolicyRuntimeRule rule(String actions, String purposes) {
        AuthzPolicyRuntimeRule rule = new AuthzPolicyRuntimeRule();
        rule.setActionCodesJson(actions);
        rule.setPurposeCodesJson(purposes);
        return rule;
    }
}

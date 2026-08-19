package com.xfwang.platform.auth.application.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeRule;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeResource;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyRuntimeSubject;
import com.xfwang.platform.auth.domain.service.SubjectSelectorMatcher;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 统一运行时策略规则的动作、用途和主体候选预筛选。 */
@Component
@RequiredArgsConstructor
public class RuntimePolicyRuleMatcher {

    private final AuthorizationJsonCodec jsonCodec;
    private final SubjectSelectorMatcher subjectMatcher;

    public boolean matches(AuthzPolicyRuntimeRule rule,
                           Collection<AuthzPolicyRuntimeSubject> selectors,
                           SubjectContext subject, String action, String purpose) {
        if (rule == null || StringUtils.isAnyBlank(action, purpose)) {
            return false;
        }
        try {
            return contains(rule.getActionCodesJson(), action)
                && contains(rule.getPurposeCodesJson(), purpose)
                && safe(selectors).stream().anyMatch(selector -> subjectMatcher.matches(
                    selector.getSubjectType(), selector.getSubjectId(), selector.getIncludeChildren(), subject));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean matchesResource(AuthzPolicyRuntimeRule rule,
                                   Collection<AuthzPolicyRuntimeResource> bindings,
                                   Long resourceId, Set<Long> lineage) {
        if (rule == null || resourceId == null || lineage == null) {
            return false;
        }
        if ("TYPE".equals(rule.getResourceScope())) {
            return true;
        }
        return safe(bindings).stream().anyMatch(binding -> lineage.contains(binding.getResourceId())
            && (Objects.equals(binding.getResourceId(), resourceId)
            || Boolean.TRUE.equals(binding.getIncludeChildren())));
    }

    private boolean contains(String json, String code) {
        return StringUtils.isNotBlank(json)
            && jsonCodec.read(json, new TypeReference<Set<String>>() { }).contains(code);
    }

    private <T> Collection<T> safe(Collection<T> values) {
        return values == null ? List.of() : values;
    }
}

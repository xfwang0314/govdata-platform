package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.enums.SubjectType;
import com.xfwang.platform.api.auth.model.SubjectContext;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 统一 Policy、Grant、资源范围和平台行范围的主体选择器语义。 */
public class SubjectSelectorMatcher {

    public boolean matches(String type, String id, Boolean includeChildren, SubjectContext subject) {
        if (subject == null || type == null || type.isBlank()) {
            return false;
        }
        try {
            return switch (SubjectType.valueOf(type.toUpperCase(Locale.ROOT))) {
                case ANY -> true;
                case HUMAN -> Objects.equals(id, Objects.toString(subject.getUserId(), null));
                case ROLE -> safe(subject.getRoleKeys()).contains(id)
                    || safe(subject.getRoleIds()).stream().map(String::valueOf).anyMatch(id::equals);
                case DEPARTMENT -> Boolean.TRUE.equals(includeChildren)
                    ? safe(subject.getDeptPathIds()).stream().map(String::valueOf).anyMatch(id::equals)
                    : Objects.equals(id, Objects.toString(subject.getDeptId(), null));
                case SERVICE -> subject.getPrincipalType() == PrincipalType.SERVICE
                    && Objects.equals(id, subject.getPrincipalId());
                case AGENT -> subject.getPrincipalType() == PrincipalType.AGENT
                    && Objects.equals(id, subject.getPrincipalId());
            };
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private <T> Collection<T> safe(Collection<T> values) {
        return values == null ? List.of() : values;
    }
}

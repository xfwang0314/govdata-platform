package com.xfwang.platform.auth.domain.policy;

import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.auth.domain.policy.model.PolicyCondition;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 受限条件求值器，不执行脚本、SpEL 或 SQL。
 */
@Component
public class PolicyConditionEvaluator {

    public boolean matches(PolicyConditionGroup group, SubjectContext subject,
                           ResourceDescriptor resource, EnvironmentContext environment) {
        if (group == null) {
            return true;
        }
        List<PolicyCondition> conditions = group.getConditions() == null ? List.of() : group.getConditions();
        List<PolicyConditionGroup> groups = group.getGroups() == null ? List.of() : group.getGroups();
        boolean any = "ANY".equalsIgnoreCase(group.getLogic());
        if (conditions.isEmpty() && groups.isEmpty()) {
            return true;
        }
        if (any) {
            return conditions.stream().anyMatch(condition -> matches(condition, subject, resource, environment))
                || groups.stream().anyMatch(child -> matches(child, subject, resource, environment));
        }
        return conditions.stream().allMatch(condition -> matches(condition, subject, resource, environment))
            && groups.stream().allMatch(child -> matches(child, subject, resource, environment));
    }

    boolean matches(PolicyCondition condition, SubjectContext subject,
                    ResourceDescriptor resource, EnvironmentContext environment) {
        if (condition == null || condition.getOperator() == null) {
            return false;
        }
        Object left = AttributeResolver.resolve(condition.getLeft(), subject, resource, environment);
        if (condition.getOperator() == ConditionOperator.EXISTS) {
            return left != null;
        }
        if (left == null) {
            return false;
        }
        Object right = condition.getRightAttribute() == null || condition.getRightAttribute().isBlank()
            ? condition.getValue()
            : AttributeResolver.resolve(condition.getRightAttribute(), subject, resource, environment);
        if (right == null) {
            return false;
        }
        return evaluate(condition.getOperator(), left, right);
    }

    private boolean evaluate(ConditionOperator operator, Object left, Object right) {
        return switch (operator) {
            case EXISTS -> left != null;
            case EQ -> equalValue(left, right);
            case NE -> !equalValue(left, right);
            case IN -> right instanceof Collection<?> values && values.stream().anyMatch(value -> equalValue(left, value));
            case NOT_IN -> right instanceof Collection<?> values
                && values.stream().noneMatch(value -> equalValue(left, value));
            case CONTAINS -> AttributeResolver.contains(left, right);
            case GT -> left != null && right != null && compare(left, right) > 0;
            case GTE -> left != null && right != null && compare(left, right) >= 0;
            case LT -> left != null && right != null && compare(left, right) < 0;
            case LTE -> left != null && right != null && compare(left, right) <= 0;
        };
    }

    private boolean equalValue(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return number(left).compareTo(number(right)) == 0;
        }
        return Objects.equals(left, right);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return number(left).compareTo(number(right));
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return left.toString().compareTo(right.toString());
    }

    private BigDecimal number(Object value) {
        return new BigDecimal(value.toString());
    }
}

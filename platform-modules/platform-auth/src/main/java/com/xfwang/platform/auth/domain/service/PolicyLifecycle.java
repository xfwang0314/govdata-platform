package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.enums.PolicyAggregateStatus;
import com.xfwang.platform.api.auth.enums.PolicyVersionStatus;
import com.xfwang.platform.auth.domain.enums.PolicyLifecycleEvent;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ABAC 策略轻量状态机。这里只描述状态迁移，不访问数据库、登录上下文或外部能力。
 */
@Component
public class PolicyLifecycle {

    private static final Map<PolicyAggregateStatus,
        Map<PolicyLifecycleEvent, PolicyAggregateStatus>> AGGREGATE_TRANSITIONS = Map.of(
            PolicyAggregateStatus.DRAFT, Map.of(
                PolicyLifecycleEvent.EDIT_DRAFT, PolicyAggregateStatus.DRAFT,
                PolicyLifecycleEvent.PUBLISH, PolicyAggregateStatus.ACTIVE),
            PolicyAggregateStatus.ACTIVE, Map.of(
                PolicyLifecycleEvent.DISABLE, PolicyAggregateStatus.DISABLED),
            PolicyAggregateStatus.DISABLED, Map.of(
                PolicyLifecycleEvent.EDIT_DRAFT, PolicyAggregateStatus.DISABLED,
                PolicyLifecycleEvent.CREATE_VERSION, PolicyAggregateStatus.DISABLED,
                PolicyLifecycleEvent.PUBLISH, PolicyAggregateStatus.ACTIVE,
                PolicyLifecycleEvent.ENABLE, PolicyAggregateStatus.ACTIVE));

    private static final Map<PolicyVersionStatus,
        Map<PolicyLifecycleEvent, PolicyVersionStatus>> VERSION_TRANSITIONS = Map.of(
            PolicyVersionStatus.DRAFT, Map.of(
                PolicyLifecycleEvent.EDIT_DRAFT, PolicyVersionStatus.DRAFT,
                PolicyLifecycleEvent.PUBLISH, PolicyVersionStatus.PUBLISHED,
                PolicyLifecycleEvent.DISCARD_PENDING, PolicyVersionStatus.DISCARDED),
            PolicyVersionStatus.PUBLISHED, Map.of(
                PolicyLifecycleEvent.REPLACE_CURRENT, PolicyVersionStatus.SUPERSEDED));

    public String transitionAggregate(String currentStatus, PolicyLifecycleEvent event) {
        return transitionAggregateStatus(currentStatus, event).code();
    }

    public String transitionVersion(String currentStatus, PolicyLifecycleEvent event) {
        return transitionVersionStatus(currentStatus, event).code();
    }

    private PolicyAggregateStatus transitionAggregateStatus(String currentStatus, PolicyLifecycleEvent event) {
        PolicyAggregateStatus current;
        try {
            current = PolicyAggregateStatus.valueOf(currentStatus);
        } catch (RuntimeException ex) {
            throw invalid("策略当前状态无效: " + currentStatus);
        }
        PolicyAggregateStatus next = AGGREGATE_TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw invalid("策略不允许从 " + current.label() + " 执行 " + event.name());
        }
        return next;
    }

    private PolicyVersionStatus transitionVersionStatus(String currentStatus, PolicyLifecycleEvent event) {
        PolicyVersionStatus current;
        try {
            current = PolicyVersionStatus.valueOf(currentStatus);
        } catch (RuntimeException ex) {
            throw invalid("策略版本当前状态无效: " + currentStatus);
        }
        PolicyVersionStatus next = VERSION_TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw invalid("策略版本不允许从 " + current.label() + " 执行 " + event.name());
        }
        return next;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(GlobalErrorCode.VALIDATION_ERROR, message);
    }
}

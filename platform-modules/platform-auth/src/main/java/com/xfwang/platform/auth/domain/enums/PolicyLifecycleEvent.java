package com.xfwang.platform.auth.domain.enums;

/** 驱动策略聚合与版本状态迁移的领域事件。 */
public enum PolicyLifecycleEvent {
    EDIT_DRAFT,
    CREATE_VERSION,
    PUBLISH,
    DISABLE,
    ENABLE,
    DISCARD_PENDING,
    REPLACE_CURRENT
}

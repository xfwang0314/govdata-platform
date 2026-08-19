package com.xfwang.platform.api.auth.catalog;

/** 资源类型在授权管理端和运行时开放的能力。 */
public enum ResourceAuthorizationCapability {
    RESOURCE_CATALOG,
    CUSTOM_POLICY,
    POLICY_TEMPLATE,
    QUERY_SCOPE,
    GRANT,
    INSTANCE_DECISION,
    TYPE_CONTEXT_DECISION
}

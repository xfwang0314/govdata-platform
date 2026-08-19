package com.xfwang.platform.api.auth.catalog;

/** 策略绑定某类资源时允许使用的授权粒度。 */
public enum ResourceSelectionMode {
    /** 按资源类型绑定，不要求存在逐条 Resource 投影。 */
    TYPE,
    /** 按 authz_resource 中的具体投影实例绑定。 */
    INSTANCE
}

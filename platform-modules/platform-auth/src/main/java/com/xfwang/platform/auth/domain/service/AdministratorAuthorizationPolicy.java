package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.model.SubjectContext;

import java.util.Set;

/**
 * 统一识别允许在当前租户内绕过 ABAC Policy/Grant 的平台管理员。
 *
 * <p>本策略只回答主体是否具有运维直通身份，不绕过资源存在性、租户、状态和类型校验。</p>
 */
public class AdministratorAuthorizationPolicy {

    private static final Set<String> EXPLICIT_AUTHORIZATION_ACTIONS = Set.of(
        "QUERY_DATA", "QUERY", "EXPORT", "DOWNLOAD", "SHARE", "AUTHORIZE", "DELEGATE",
        "DECRYPT_SECRET", "EXECUTE", "USE_AS_SOURCE", "USE_AS_TARGET");

    private final Long superAdminUserId;
    private final String tenantAdminRoleKey;

    public AdministratorAuthorizationPolicy(Long superAdminUserId, String tenantAdminRoleKey) {
        this.superAdminUserId = superAdminUserId;
        this.tenantAdminRoleKey = tenantAdminRoleKey;
    }

    public boolean permitsTenantOperation(SubjectContext subject) {
        return subject != null && (superAdminUserId.equals(subject.getUserId())
            || roleKeys(subject).contains(tenantAdminRoleKey));
    }

    /** 管理员只直通管理面动作，不能凭运维身份读取或共享实际数据。 */
    public boolean permits(SubjectContext subject, String action) {
        return permitsTenantOperation(subject)
            && (action == null || !EXPLICIT_AUTHORIZATION_ACTIONS.contains(action.toUpperCase(java.util.Locale.ROOT)));
    }

    private Set<String> roleKeys(SubjectContext subject) {
        return subject.getRoleKeys() == null ? Set.of() : subject.getRoleKeys();
    }
}

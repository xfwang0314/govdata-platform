package org.dromara.system.api.permission;

/** 系统管理领域在授权目录中的稳定资源类型和动作。 */
public final class SystemAuthorizationResources {

    public static final String USER = "system:user";
    public static final String DEPARTMENT = "system:department";
    public static final String POST = "system:post";
    public static final String ROLE = "system:role";

    public static final String VIEW = "VIEW";
    public static final String EXPORT = "EXPORT";
    public static final String CREATE = "CREATE";
    public static final String CREATE_CHILD = "CREATE_CHILD";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String USER_ADMINISTRATION = "SYSTEM_USER_ADMINISTRATION";
    public static final String DEPARTMENT_ADMINISTRATION = "SYSTEM_DEPARTMENT_ADMINISTRATION";
    public static final String POST_ADMINISTRATION = "SYSTEM_POST_ADMINISTRATION";
    public static final String ROLE_ADMINISTRATION = "SYSTEM_ROLE_ADMINISTRATION";

    private SystemAuthorizationResources() {
    }
}

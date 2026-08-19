package com.xfwang.platform.auth.application.catalog;

import com.xfwang.platform.api.auth.catalog.AuthorizationActionDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationCatalogContributor;
import com.xfwang.platform.api.auth.catalog.AuthorizationPurposeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceSelectionMode;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 为首批迁移到 ABAC 的系统管理数据注册授权 Schema。 */
@Component
public class SystemManagementAuthorizationCatalogContributor implements AuthorizationCatalogContributor {

    private static final String MODULE = "ruoyi-system";
    private static final String DOMAIN = "SYSTEM_MANAGEMENT";

    @Override
    public String moduleCode() {
        return MODULE;
    }

    @Override
    public String moduleLabel() {
        return "系统管理";
    }

    @Override
    public List<ResourceTypeDefinition> resourceTypes() {
        return List.of(
            type(SystemAuthorizationResources.USER, "用户数据", true),
            type(SystemAuthorizationResources.DEPARTMENT, "部门数据", false),
            type(SystemAuthorizationResources.POST, "岗位数据", true),
            type(SystemAuthorizationResources.ROLE, "角色数据", true));
    }

    @Override
    public List<AuthorizationPurposeDefinition> purposes() {
        return List.of(
            new AuthorizationPurposeDefinition(SystemAuthorizationResources.USER_ADMINISTRATION,
                "用户管理", Set.of(SystemAuthorizationResources.USER)),
            new AuthorizationPurposeDefinition(SystemAuthorizationResources.DEPARTMENT_ADMINISTRATION,
                "部门管理", Set.of(SystemAuthorizationResources.DEPARTMENT)),
            new AuthorizationPurposeDefinition(SystemAuthorizationResources.POST_ADMINISTRATION,
                "岗位管理", Set.of(SystemAuthorizationResources.POST)),
            new AuthorizationPurposeDefinition(SystemAuthorizationResources.ROLE_ADMINISTRATION,
                "角色管理", Set.of(SystemAuthorizationResources.ROLE)));
    }

    private ResourceTypeDefinition type(String typeCode, String typeLabel, boolean exportable) {
        List<AuthorizationActionDefinition> actions = exportable
            ? List.of(action(SystemAuthorizationResources.VIEW, "查看", "LOW"),
                action(SystemAuthorizationResources.EXPORT, "导出", "MEDIUM"),
                action(SystemAuthorizationResources.CREATE, "新增", "MEDIUM"),
                action(SystemAuthorizationResources.UPDATE, "修改", "HIGH"),
                action(SystemAuthorizationResources.DELETE, "删除", "CRITICAL"))
            : List.of(action(SystemAuthorizationResources.VIEW, "查看", "LOW"),
                action(SystemAuthorizationResources.CREATE_CHILD, "新增下级部门", "HIGH"),
                action(SystemAuthorizationResources.UPDATE, "修改", "HIGH"),
                action(SystemAuthorizationResources.DELETE, "删除", "CRITICAL"));
        return new ResourceTypeDefinition(DOMAIN, "系统管理", MODULE, typeCode, typeLabel,
            Set.of(), Set.of(ResourceSelectionMode.TYPE),
            Set.of(ResourceAuthorizationCapability.CUSTOM_POLICY,
                ResourceAuthorizationCapability.QUERY_SCOPE), actions, List.of());
    }

    private AuthorizationActionDefinition action(String code, String label, String riskLevel) {
        return new AuthorizationActionDefinition(code, label, riskLevel);
    }
}

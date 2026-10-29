package com.xfwang.platform.api.auth.catalog;

import java.util.List;

/** 业务模块向 platform-auth 贡献资源类型、动作、属性和用途的扩展 seam。 */
public interface AuthorizationCatalogContributor {

    String moduleCode();

    /** 面向管理端展示的稳定模块名称。 */
    String moduleLabel();

    /** 模块资源在管理端授权目录中的虚拟分类，不参与真实 Resource 层级。 */
    ResourceNavigationCategory navigationCategory();

    List<ResourceTypeDefinition> resourceTypes();

    default List<AuthorizationPurposeDefinition> purposes() {
        return List.of();
    }
}

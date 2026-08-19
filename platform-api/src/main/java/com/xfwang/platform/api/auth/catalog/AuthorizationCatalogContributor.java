package com.xfwang.platform.api.auth.catalog;

import java.util.List;

/** 业务模块向 platform-auth 贡献资源类型、动作、属性和用途的扩展 seam。 */
public interface AuthorizationCatalogContributor {

    String moduleCode();

    /** 面向管理端展示的稳定模块名称。 */
    String moduleLabel();

    List<ResourceTypeDefinition> resourceTypes();

    default List<AuthorizationPurposeDefinition> purposes() {
        return List.of();
    }
}

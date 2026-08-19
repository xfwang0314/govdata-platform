package org.dromara.system.service.support;

import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.dromara.common.mybatis.annotation.ScopeColumn;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.bo.SysRoleBo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.mapper.SysPostMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemOrganizationResourceContractTest {

    @Test
    void postAndRoleListsDeclareDepartmentScopedAbacResources() throws NoSuchMethodException {
        assertScope(SysPostMapper.class.getMethod("selectPagePostList",
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.class,
                com.baomidou.mybatisplus.core.conditions.Wrapper.class),
            SystemAuthorizationResources.POST, SystemAuthorizationResources.POST_ADMINISTRATION, "dept_id");
        assertScope(SysRoleMapper.class.getMethod("selectPageRoleList",
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.class,
                com.baomidou.mybatisplus.core.conditions.Wrapper.class),
            SystemAuthorizationResources.ROLE, SystemAuthorizationResources.ROLE_ADMINISTRATION, "owner_dept_id");
    }

    @Test
    void roleOwnershipIsAnExplicitBusinessFieldAcrossPersistenceAndHttpModels() throws NoSuchFieldException {
        assertThat(SysRole.class.getDeclaredField("ownerDeptId").getType()).isEqualTo(Long.class);
        assertThat(SysRoleBo.class.getDeclaredField("ownerDeptId").getType()).isEqualTo(Long.class);
        assertThat(SysRoleVo.class.getDeclaredField("ownerDeptId").getType()).isEqualTo(Long.class);
    }

    private void assertScope(java.lang.reflect.Method method, String resourceType,
                             String purpose, String ownerColumn) {
        AbacDataScope scope = method.getAnnotation(AbacDataScope.class);
        assertThat(scope).isNotNull();
        assertThat(scope.resourceType()).isEqualTo(resourceType);
        assertThat(scope.action()).isEqualTo(SystemAuthorizationResources.VIEW);
        assertThat(scope.purpose()).isEqualTo(purpose);
        assertThat(scope.columns()).extracting(ScopeColumn::attribute, ScopeColumn::column)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("ownerDeptId", ownerColumn));
    }
}

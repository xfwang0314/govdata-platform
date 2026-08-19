package org.dromara.system.service.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.system.api.domain.RoleDTO;
import org.dromara.system.api.permission.DataAuthorizationScopeRequest;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.dromara.system.domain.bo.SysPostBo;
import org.dromara.system.domain.bo.SysRoleBo;
import org.dromara.system.domain.SysRole;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.mapper.SysPostMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysRoleMenuMapper;
import org.dromara.system.mapper.SysUserPostMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.service.impl.SysPostServiceImpl;
import org.dromara.system.service.impl.SysRoleServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemOrganizationMutationAuthorizationTest {

    @Test
    void authorizesPostCreationAgainstItsOwningDepartmentBeforePersistence() {
        SystemDataAuthorizationGuard guard = mock(SystemDataAuthorizationGuard.class);
        doThrow(new AuthorizationReached()).when(guard).check(any(), eq(101L), isNull());
        SysPostServiceImpl service = new SysPostServiceImpl(mock(SysPostMapper.class),
            mock(SysDeptMapper.class), mock(SysUserPostMapper.class), guard);
        SysPostBo command = new SysPostBo();
        command.setDeptId(101L);

        assertThatThrownBy(() -> service.insertPost(command)).isInstanceOf(AuthorizationReached.class);

        assertRequest(guard, SystemAuthorizationResources.POST,
            SystemAuthorizationResources.CREATE, SystemAuthorizationResources.POST_ADMINISTRATION);
    }

    @Test
    void authorizesRoleCreationAgainstItsExplicitOwningDepartmentBeforePersistence() {
        SystemDataAuthorizationGuard guard = mock(SystemDataAuthorizationGuard.class);
        doThrow(new AuthorizationReached()).when(guard).check(any(), eq(102L), isNull());
        SysRoleServiceImpl service = new SysRoleServiceImpl(mock(SysRoleMapper.class),
            mock(SysRoleMenuMapper.class), mock(SysUserRoleMapper.class), guard);
        SysRoleBo command = new SysRoleBo();
        command.setOwnerDeptId(102L);

        assertThatThrownBy(() -> service.insertRole(command)).isInstanceOf(AuthorizationReached.class);

        assertRequest(guard, SystemAuthorizationResources.ROLE,
            SystemAuthorizationResources.CREATE, SystemAuthorizationResources.ROLE_ADMINISTRATION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsActiveRoleSubjectsInsideTenantWithoutRoleResourceAbac() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SystemDataAuthorizationGuard guard = mock(SystemDataAuthorizationGuard.class);
        SysRole role = new SysRole();
        role.setRoleId(3L);
        role.setRoleName("部门管理员");
        role.setRoleKey("dept-admin");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        SysRoleServiceImpl service = new SysRoleServiceImpl(roleMapper,
            mock(SysRoleMenuMapper.class), mock(SysUserRoleMapper.class), guard);

        List<RoleDTO> result = service.selectActiveRolesForAuthorization();

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getRoleId()).isEqualTo(3L);
            assertThat(item.getRoleKey()).isEqualTo("dept-admin");
        });
        verifyNoInteractions(guard);
    }

    private void assertRequest(SystemDataAuthorizationGuard guard, String resourceType,
                               String action, String purpose) {
        ArgumentCaptor<DataAuthorizationScopeRequest> request =
            ArgumentCaptor.forClass(DataAuthorizationScopeRequest.class);
        verify(guard).check(request.capture(), any(), isNull());
        assertThat(request.getValue()).isEqualTo(
            new DataAuthorizationScopeRequest(resourceType, action, purpose));
    }

    private static final class AuthorizationReached extends RuntimeException {
    }
}

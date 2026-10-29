package org.dromara.system.service.impl;

import org.dromara.common.core.constant.SystemConstants;
import org.dromara.system.api.domain.UserAuthorizationProfileDTO;
import org.dromara.system.domain.vo.SysDeptVo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.mapper.SysPostMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserPostMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.service.support.SystemDataAuthorizationGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysDeptMapper deptMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysPostMapper postMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysUserPostMapper userPostMapper;
    @Mock
    private SystemDataAuthorizationGuard authorizationGuard;

    @Test
    void authorizationProfileOnlyContainsActiveRoles() {
        SysUserVo user = new SysUserVo();
        user.setTenantId("103");
        user.setUserId(1001L);
        user.setDeptId(2001L);
        user.setStatus(SystemConstants.NORMAL);
        SysDeptVo dept = new SysDeptVo();
        dept.setDeptId(2001L);
        dept.setAncestors("100,200");
        SysRoleVo active = role(3001L, "metadata_viewer", SystemConstants.NORMAL);
        SysRoleVo disabled = role(3002L, "disabled_role", SystemConstants.DISABLE);
        when(userMapper.selectVoById(1001L)).thenReturn(user);
        when(roleMapper.selectRolesByUserId(1001L)).thenReturn(List.of(active, disabled));
        when(deptMapper.selectVoById(2001L)).thenReturn(dept);

        SysUserServiceImpl service = new SysUserServiceImpl(userMapper, deptMapper, roleMapper,
            postMapper, userRoleMapper, userPostMapper, authorizationGuard);
        UserAuthorizationProfileDTO profile = service.selectAuthorizationProfileById(1001L);

        assertThat(profile.getRoleIds()).isEqualTo(Set.of(3001L));
        assertThat(profile.getRoleKeys()).isEqualTo(Set.of("metadata_viewer"));
        assertThat(profile.getDeptPathIds()).containsExactlyInAnyOrder(100L, 200L, 2001L);
    }

    private SysRoleVo role(Long roleId, String roleKey, String status) {
        SysRoleVo role = new SysRoleVo();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        role.setStatus(status);
        return role;
    }
}

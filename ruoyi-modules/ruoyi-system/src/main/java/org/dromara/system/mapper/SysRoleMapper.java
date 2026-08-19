package org.dromara.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.dromara.common.mybatis.annotation.ScopeColumn;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.vo.SysRoleVo;

import java.util.List;

/**
 * 角色表 数据层
 *
 * @author Lion Li
 */
public interface SysRoleMapper extends BaseMapperPlus<SysRole, SysRoleVo> {

    /**
     * 构建根据用户ID查询角色ID的SQL子查询
     *
     * @param userId 用户ID
     * @return 查询用户对应角色ID的SQL语句字符串
     */
    default String buildRoleByUserSql(Long userId) {
        return """
                select role_id from sys_user_role where user_id = %d
            """.formatted(userId);
    }

    /**
     * 分页查询角色列表
     *
     * @param page         分页对象
     * @param queryWrapper 查询条件
     * @return 包含角色信息的分页结果
     */
    @AbacDataScope(resourceType = SystemAuthorizationResources.ROLE,
        action = SystemAuthorizationResources.VIEW,
        purpose = SystemAuthorizationResources.ROLE_ADMINISTRATION, columns = {
        @ScopeColumn(attribute = "ownerDeptId", column = "owner_dept_id")
    })
    default Page<SysRoleVo> selectPageRoleList(@Param("page") Page<SysRole> page, @Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 根据条件查询角色数据
     *
     * @param queryWrapper 查询条件
     * @return 角色数据集合信息
     */
    @AbacDataScope(resourceType = SystemAuthorizationResources.ROLE,
        action = SystemAuthorizationResources.VIEW,
        purpose = SystemAuthorizationResources.ROLE_ADMINISTRATION, columns = {
        @ScopeColumn(attribute = "ownerDeptId", column = "owner_dept_id")
    })
    default List<SysRoleVo> selectRoleList(@Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /** 导出角色时使用独立动作执行 ABAC 范围。 */
    @AbacDataScope(resourceType = SystemAuthorizationResources.ROLE,
        action = SystemAuthorizationResources.EXPORT,
        purpose = SystemAuthorizationResources.ROLE_ADMINISTRATION, columns = {
        @ScopeColumn(attribute = "ownerDeptId", column = "owner_dept_id")
    })
    default List<SysRoleVo> selectRoleExportList(@Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 根据角色ID集合查询角色数量
     *
     * @param roleIds 角色ID列表
     * @return 匹配的角色数量
     */
    default long selectRoleCount(List<Long> roleIds) {
        return this.selectCount(new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleId, roleIds));
    }

    /**
     * 根据角色ID查询角色信息
     *
     * @param roleId 角色ID
     * @return 对应的角色信息
     */
    default SysRoleVo selectRoleById(Long roleId) {
        return this.selectVoById(roleId);
    }

    /**
     * 根据用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    default List<SysRoleVo> selectRolesByUserId(Long userId) {
        return this.selectVoList(new LambdaQueryWrapper<SysRole>()
            .select(SysRole::getRoleId, SysRole::getRoleName, SysRole::getRoleKey,
                SysRole::getRoleSort, SysRole::getStatus)
            .inSql(SysRole::getRoleId, this.buildRoleByUserSql(userId)));
    }

}

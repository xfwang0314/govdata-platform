package org.dromara.system.api;

import org.dromara.system.api.domain.RoleDTO;

import java.util.List;
import java.util.Map;

/**
 * 通用 角色服务
 *
 * @author AprilWind
 */
public interface RoleService {

    /**
     * 查询当前租户内可作为授权主体的启用角色。
     *
     * <p>该入口只供授权策略主体选择使用，不套用角色资源 ABAC，避免策略配置形成自锁；
     * 租户隔离仍由系统持久化层统一执行。</p>
     *
     * @return 启用角色稳定标识
     */
    List<RoleDTO> selectActiveRolesForAuthorization();

    /**
     * 根据角色 ID 列表查询角色名称映射关系
     *
     * @param roleIds 角色 ID 列表
     * @return Map，其中 key 为角色 ID，value 为对应的角色名称
     */
    Map<Long, String> selectRoleNamesByIds(List<Long> roleIds);

}

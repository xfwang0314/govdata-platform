package org.dromara.system.api;

import org.dromara.system.api.domain.DeptDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用 部门服务
 *
 * @author Lion Li
 */
public interface DeptService {

    /**
     * 通过部门ID查询部门名称
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    String selectDeptNameByIds(String deptIds);

    /**
     * 根据部门ID查询部门负责人
     *
     * @param deptId 部门ID，用于指定需要查询的部门
     * @return 返回该部门的负责人ID
     */
    Long selectDeptLeaderById(Long deptId);

    /**
     * 查询部门
     *
     * @return 部门列表
     */
    List<DeptDTO> selectDeptsByList();

    /**
     * 根据部门 ID 列表查询部门名称映射关系
     *
     * @param deptIds 部门 ID 列表
     * @return Map，其中 key 为部门 ID，value 为对应的部门名称
     */
    Map<Long, String> selectDeptNamesByIds(List<Long> deptIds);

    /**
     * 查询指定部门从根部门到自身的稳定 ID 路径。
     *
     * @param deptId 部门 ID
     * @return 祖先部门及当前部门 ID
     */
    Set<Long> selectDeptPathIds(Long deptId);

    /**
     * 查询指定部门及其全部下级部门 ID。
     *
     * @param deptId 部门 ID
     * @return 当前部门及全部下级部门 ID
     */
    Set<Long> selectDeptAndChildIds(Long deptId);

}

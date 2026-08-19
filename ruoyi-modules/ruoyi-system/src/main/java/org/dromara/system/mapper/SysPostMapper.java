package org.dromara.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.dromara.common.mybatis.annotation.ScopeColumn;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.dromara.system.domain.SysPost;
import org.dromara.system.domain.vo.SysPostVo;

import java.util.List;

/**
 * 岗位信息 数据层
 *
 * @author Lion Li
 */
public interface SysPostMapper extends BaseMapperPlus<SysPost, SysPostVo> {

    /**
     * 分页查询岗位列表
     *
     * @param page         分页对象
     * @param queryWrapper 查询条件
     * @return 包含岗位信息的分页结果
     */
    @AbacDataScope(resourceType = SystemAuthorizationResources.POST,
        action = SystemAuthorizationResources.VIEW,
        purpose = SystemAuthorizationResources.POST_ADMINISTRATION, columns = {
        @ScopeColumn(attribute = "ownerDeptId", column = "dept_id")
    })
    default Page<SysPostVo> selectPagePostList(Page<SysPost> page, Wrapper<SysPost> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 查询岗位列表
     *
     * @param queryWrapper 查询条件
     * @return 岗位信息列表
     */
    @AbacDataScope(resourceType = SystemAuthorizationResources.POST,
        action = SystemAuthorizationResources.VIEW,
        purpose = SystemAuthorizationResources.POST_ADMINISTRATION, columns = {
        @ScopeColumn(attribute = "ownerDeptId", column = "dept_id")
    })
    default List<SysPostVo> selectPostList(Wrapper<SysPost> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /** 导出岗位时使用独立动作执行 ABAC 范围。 */
    @AbacDataScope(resourceType = SystemAuthorizationResources.POST,
        action = SystemAuthorizationResources.EXPORT,
        purpose = SystemAuthorizationResources.POST_ADMINISTRATION, columns = {
        @ScopeColumn(attribute = "ownerDeptId", column = "dept_id")
    })
    default List<SysPostVo> selectPostExportList(Wrapper<SysPost> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 根据岗位ID集合查询岗位数量
     *
     * @param postIds 岗位ID列表
     * @return 匹配的岗位数量
     */
    default long selectPostCount(List<Long> postIds) {
        return this.selectCount(new LambdaQueryWrapper<SysPost>().in(SysPost::getPostId, postIds));
    }

    /**
     * 根据用户ID查询其关联的岗位列表
     *
     * @param userId 用户ID
     * @return 岗位信息列表
     */
    default List<SysPostVo> selectPostsByUserId(Long userId) {
        return this.selectVoList(new LambdaQueryWrapper<SysPost>()
            .inSql(SysPost::getPostId, "select post_id from sys_user_post where user_id = " + userId));
    }

}

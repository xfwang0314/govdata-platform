package org.dromara.system.api.permission;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * ABAC 为关系型列表查询计算出的结构化数据范围。
 *
 * <p>该契约只表达可执行的范围，不携带 SQL，SQL 列映射由调用方控制。</p>
 */
public record DataAuthorizationScope(boolean unrestricted,
                                     boolean denied,
                                     Set<Long> departmentIds,
                                     boolean includeCurrentUser) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public DataAuthorizationScope {
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        if (unrestricted && (denied || !departmentIds.isEmpty() || includeCurrentUser)) {
            throw new IllegalArgumentException("不受限数据范围不能同时包含其他限制");
        }
        if (denied && (!departmentIds.isEmpty() || includeCurrentUser)) {
            throw new IllegalArgumentException("拒绝数据范围不能同时包含允许条件");
        }
    }

    public static DataAuthorizationScope unrestrictedScope() {
        return new DataAuthorizationScope(true, false, Set.of(), false);
    }

    public static DataAuthorizationScope deniedScope() {
        return new DataAuthorizationScope(false, true, Set.of(), false);
    }

    public static DataAuthorizationScope restricted(Set<Long> departmentIds, boolean includeCurrentUser) {
        Set<Long> ids = departmentIds == null ? Set.of() : departmentIds;
        return ids.isEmpty() && !includeCurrentUser
            ? deniedScope()
            : new DataAuthorizationScope(false, false, ids, includeCurrentUser);
    }
}

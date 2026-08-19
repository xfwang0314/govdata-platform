package org.dromara.system.api.permission;

import java.util.Optional;

/**
 * 系统查询到平台 ABAC 的反腐层。
 *
 * <p>返回空表示提供方不支持该请求。调用方必须按拒绝处理，不得回退到其他数据范围实现。</p>
 */
public interface DataAuthorizationScopeProvider {

    Optional<DataAuthorizationScope> resolve(DataAuthorizationScopeRequest request);
}

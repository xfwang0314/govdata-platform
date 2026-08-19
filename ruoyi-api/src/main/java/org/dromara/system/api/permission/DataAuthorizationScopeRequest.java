package org.dromara.system.api.permission;

import java.io.Serial;
import java.io.Serializable;
/** 当前 Mapper 查询请求 ABAC 数据范围所需的稳定上下文。 */
public record DataAuthorizationScopeRequest(String resourceType,
                                            String action,
                                            String purpose) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

}

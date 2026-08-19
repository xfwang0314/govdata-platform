package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;

/** 为领域列表查询提供分页前的 ABAC 可见资源集合。 */
public interface AuthorizedResourceService {

    /** 返回当前主体在当前租户内的批量资源范围；管理员可以返回不受限范围。 */
    AuthorizedResourceScope resolveScope(AuthorizedResourceQuery query);
}

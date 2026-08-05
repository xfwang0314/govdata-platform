package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;

/**
 * 通用动态授权服务契约。
 */
public interface AuthorizationService {

    /**
     * 返回完整授权决策，不抛出拒绝异常。
     */
    AccessDecision decide(AccessRequest request);

    /**
     * 执行授权校验，拒绝时抛出统一权限异常。
     */
    void check(AccessRequest request);
}

package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;

import java.util.Optional;

/**
 * 供 MyBatis 和外部查询网关读取当前授权决策。
 */
public interface AuthorizationContextAccessor {

    Optional<AccessRequest> currentRequest();

    Optional<AccessDecision> currentDecision();
}

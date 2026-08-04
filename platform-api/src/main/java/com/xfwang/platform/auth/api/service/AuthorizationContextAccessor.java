package com.xfwang.platform.auth.api.service;

import com.xfwang.platform.auth.api.model.AccessDecision;
import com.xfwang.platform.auth.api.model.AccessRequest;

import java.util.Optional;

/**
 * 供 MyBatis 和外部查询网关读取当前授权决策。
 */
public interface AuthorizationContextAccessor {

    Optional<AccessRequest> currentRequest();

    Optional<AccessDecision> currentDecision();
}

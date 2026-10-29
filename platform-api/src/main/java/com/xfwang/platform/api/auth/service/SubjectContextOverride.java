package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.SubjectContext;

import java.util.Optional;

/** 为非 Sa-Token 调用（例如 MCP Agent）提供显式主体上下文。 */
public interface SubjectContextOverride {

    Optional<SubjectContext> currentSubject();
}

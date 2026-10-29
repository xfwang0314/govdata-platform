package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.SubjectContext;

/**
 * 当前访问主体属性提供器。
 */
public interface SubjectProvider {

    SubjectContext currentSubject();

    /** 基于用户当前授权属性构建 HUMAN 主体，不依赖登录会话。 */
    SubjectContext subjectForUser(String tenantId, Long userId);
}

package com.xfwang.platform.auth.api.service;

import com.xfwang.platform.auth.api.model.SubjectContext;

/**
 * 当前访问主体属性提供器。
 */
public interface SubjectProvider {

    SubjectContext currentSubject();
}

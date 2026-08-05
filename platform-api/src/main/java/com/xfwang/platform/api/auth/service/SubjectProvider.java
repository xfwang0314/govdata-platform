package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.SubjectContext;

/**
 * 当前访问主体属性提供器。
 */
public interface SubjectProvider {

    SubjectContext currentSubject();
}

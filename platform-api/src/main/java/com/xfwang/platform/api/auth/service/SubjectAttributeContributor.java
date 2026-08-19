package com.xfwang.platform.api.auth.service;

import com.xfwang.platform.api.auth.model.SubjectAttributeRequest;

import java.util.Map;

/** 用户密级、行政区划等扩展 Subject 属性的贡献 seam。 */
public interface SubjectAttributeContributor {

    String contributorCode();

    Map<String, Object> attributes(SubjectAttributeRequest subject);
}

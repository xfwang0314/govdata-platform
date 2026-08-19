package com.xfwang.platform.auth.application.policy.view;

import com.xfwang.platform.auth.domain.policy.model.PolicyDocument;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** 不可变策略组版本视图。 */
@Value
@Builder
public class PolicyVersionView {
    Long policyGroupVersionId;
    Integer versionNo;
    Integer schemaVersion;
    String versionStatus;
    Integer lockVersion;
    String contentHash;
    PolicyDocument document;
    Long createdBy;
    LocalDateTime createdTime;
    Long publishedBy;
    LocalDateTime publishedTime;
}

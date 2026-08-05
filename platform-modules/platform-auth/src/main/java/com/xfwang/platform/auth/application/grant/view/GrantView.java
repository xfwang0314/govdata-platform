package com.xfwang.platform.auth.application.grant.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** Grant 管理视图。 */
@Value
@Builder
public class GrantView {
    Long grantId;
    String grantCode;
    String sourceType;
    String sourceId;
    String granteeType;
    String granteeId;
    String purpose;
    LocalDateTime effectiveTime;
    LocalDateTime expireTime;
    String status;
}

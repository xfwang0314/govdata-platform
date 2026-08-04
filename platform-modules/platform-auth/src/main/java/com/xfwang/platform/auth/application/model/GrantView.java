package com.xfwang.platform.auth.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** Grant 管理视图。 */
@Value
@Builder
public class GrantView {
    Long grantId;
    String grantNo;
    String sourceType;
    String sourceId;
    String granteeType;
    String granteeId;
    String purpose;
    LocalDateTime effectiveTime;
    LocalDateTime expireTime;
    String status;
}

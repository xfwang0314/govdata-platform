package com.xfwang.platform.api.secret;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/** 一次受控的凭据解析请求，包含租户、调用方、用途和审计上下文。 */
@Value
@Builder
public class SecretResolveRequest {
    String tenantId;
    String secretRef;
    String callerModule;
    String actorType;
    String actorId;
    String purpose;
    String traceId;
    @Builder.Default
    Set<String> requiredKeys = Set.of();
}

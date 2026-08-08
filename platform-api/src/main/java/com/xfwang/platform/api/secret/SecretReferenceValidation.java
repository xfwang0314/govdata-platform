package com.xfwang.platform.api.secret;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/** 保存业务配置前对凭据引用执行的服务端校验。 */
@Value
@Builder
public class SecretReferenceValidation {
    String secretRef;
    String callerModule;
    SecretType secretType;
    String purpose;
    @Builder.Default
    Set<String> requiredKeys = Set.of();
}

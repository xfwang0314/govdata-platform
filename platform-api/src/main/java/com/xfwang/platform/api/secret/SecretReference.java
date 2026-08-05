package com.xfwang.platform.api.secret;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** 调用模块可选择的凭据元数据，不包含任何 SecretValue。 */
@Value
@Builder
public class SecretReference {
    String secretRef;
    String secretName;
    String secretType;
    LocalDateTime expireTime;
}

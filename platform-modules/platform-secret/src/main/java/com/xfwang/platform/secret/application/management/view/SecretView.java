package com.xfwang.platform.secret.application.management.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class SecretView {
    Long secretId;
    String secretRef;
    String secretName;
    String description;
    String secretType;
    String ownerModule;
    String purposeScope;
    String status;
    LocalDateTime expireTime;
    Integer currentVersionNo;
    Integer lockVersion;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    boolean configured;
}

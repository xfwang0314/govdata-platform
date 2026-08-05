package com.xfwang.platform.secret.application.management.query;

import lombok.Data;

@Data
public class SecretQuery {
    private String secretName;
    private String secretType;
    private String ownerModule;
    private String status;
}

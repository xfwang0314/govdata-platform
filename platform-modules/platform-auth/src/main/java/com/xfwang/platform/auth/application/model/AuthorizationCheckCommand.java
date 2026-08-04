package com.xfwang.platform.auth.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 使用当前登录主体执行授权模拟的输入。 */
@Data
public class AuthorizationCheckCommand {
    @NotNull private Long resourceId;
    private String resourceType;
    @NotBlank private String action;
    private String purpose;
}

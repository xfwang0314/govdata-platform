package com.xfwang.platform.auth.application.decision.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 授权模拟和排障输入参数。
 */
@Data
public class AuthorizationCheckCommand {
    /** 模拟主体类型（HUMAN、DEPARTMENT、ROLE，留空表示使用当前登录主体）。 */
    private String subjectType;
    /** 模拟主体标识（用户ID、部门ID或角色ID）。 */
    private String subjectId;

    /** 目标用户 ID（兼容旧字段）。 */
    private Long userId;

    @NotNull
    private Long resourceId;
    private String resourceType;
    @NotBlank
    private String action;
    /** 访问目的。 */
    @NotBlank(message = "访问用途不能为空")
    private String purpose;
}


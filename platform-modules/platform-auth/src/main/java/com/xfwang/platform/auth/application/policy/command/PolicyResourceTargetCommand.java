package com.xfwang.platform.auth.application.policy.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 策略规则中的一个资源实例目标。 */
@Data
public class PolicyResourceTargetCommand {

    @NotNull
    private Long resourceId;

    private Boolean includeChildren = false;
}

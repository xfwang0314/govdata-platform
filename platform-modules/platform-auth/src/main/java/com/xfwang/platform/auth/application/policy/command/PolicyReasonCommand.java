package com.xfwang.platform.auth.application.policy.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 停用、删除等高影响生命周期操作的原因。 */
@Data
public class PolicyReasonCommand {
    @NotBlank
    @Size(max = 500)
    private String reason;
}

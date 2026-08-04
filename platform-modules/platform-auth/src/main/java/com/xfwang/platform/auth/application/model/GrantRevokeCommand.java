package com.xfwang.platform.auth.application.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Grant 撤销输入。 */
@Data
public class GrantRevokeCommand {
    @NotBlank
    private String reason;
}

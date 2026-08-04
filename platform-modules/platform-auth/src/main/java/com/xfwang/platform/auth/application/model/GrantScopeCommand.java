package com.xfwang.platform.auth.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Grant 资源动作范围输入。 */
@Data
public class GrantScopeCommand {
    @NotNull
    private Long resourceId;
    @NotBlank
    private String actionCode;
    private Boolean includeChildren = Boolean.FALSE;
    private String obligationJson;
}

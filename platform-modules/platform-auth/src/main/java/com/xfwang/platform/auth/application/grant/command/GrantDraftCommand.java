package com.xfwang.platform.auth.application.grant.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 新建 AuthorizationGrant 草稿输入。 */
@Data
public class GrantDraftCommand {
    @NotBlank private String sourceType;
    @NotBlank private String sourceId;
    @NotBlank private String grantorType;
    @NotBlank private String grantorId;
    @NotBlank private String granteeType;
    @NotBlank private String granteeId;
    @NotBlank private String purpose;
    @NotNull private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String conditionJson;
    @Valid @NotEmpty private List<GrantScopeCommand> scopes = new ArrayList<>();
}

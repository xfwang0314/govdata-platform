package com.xfwang.platform.auth.application.grant.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 新建 AuthorizationGrant 草稿输入。
 */
@Data
public class GrantDraftCommand {
    /**
     * 可信调用方或 HTTP Controller 在进入应用服务前填充，禁止接受页面自定义。
     */
    private String sourceType;
    private String sourceId;
    private String grantorType;
    private String grantorId;
    @NotBlank
    private String granteeType;
    @NotBlank
    private String granteeId;
    @NotBlank
    private String purpose;
    @NotNull
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String conditionJson;
    @Valid
    @NotEmpty
    private List<GrantScopeCommand> scopes = new ArrayList<>();
}

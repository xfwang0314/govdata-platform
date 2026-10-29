package com.xfwang.platform.mcp.application.token.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 编辑 Agent 访问凭据命令，不允许修改凭据归属和 Token。 */
@Data
public class AgentTokenUpdateCommand {

    @NotBlank
    @Size(max = 64)
    private String agentName;

    @Size(min = 1)
    private List<String> allowedCategories;

    private LocalDateTime expireTime;

    @Min(10)
    @Max(50000)
    private Integer maxDailyRequests;

    @Size(max = 200)
    private String remark;
}

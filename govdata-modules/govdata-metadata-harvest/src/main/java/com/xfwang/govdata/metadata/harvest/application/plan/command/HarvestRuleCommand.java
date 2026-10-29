package com.xfwang.govdata.metadata.harvest.application.plan.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采集过滤规则命令。
 */
@Data
public class HarvestRuleCommand {
    @NotBlank
    private String ruleScope;
    @NotBlank
    private String ruleType;
    @NotBlank
    private String matchType;
    @NotBlank
    @Size(max = 500)
    private String rulePattern;
    @NotNull
    private Integer sortNo;
}

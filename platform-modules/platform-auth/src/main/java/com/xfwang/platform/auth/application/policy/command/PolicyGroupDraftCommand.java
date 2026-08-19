package com.xfwang.platform.auth.application.policy.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 新建策略组首个完整版本草稿。 */
@Data
public class PolicyGroupDraftCommand {

    @NotBlank
    private String policyName;
    private String description;
    @NotBlank
    private String effect;
    @NotBlank
    private String riskLevel;
    private Integer priority = 100;

    /** 第一版仅支持 ANY，文档结构预留 ALL/NOT。 */
    private String subjectOperator = "ANY";

    @Valid
    @NotEmpty
    private List<PolicySubjectCommand> subjects = new ArrayList<>();

    @Valid
    @NotEmpty
    private List<PolicyRuleCommand> rules = new ArrayList<>();
}

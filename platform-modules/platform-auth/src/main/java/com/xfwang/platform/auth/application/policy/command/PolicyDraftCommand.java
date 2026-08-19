package com.xfwang.platform.auth.application.policy.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 新建不可变策略草稿输入。 */
@Data
public class PolicyDraftCommand implements PolicyContentCommand {

    @NotBlank
    private String policyName;
    @NotBlank
    private String effect;
    private String conditionJson;
    private String obligationJson;
    private Integer priority = 0;
    private String riskLevel = "LOW";

    @Valid
    @NotEmpty
    private List<PolicySubjectCommand> subjects = new ArrayList<>();

    @Valid
    @NotEmpty
    private List<PolicyResourceCommand> resources = new ArrayList<>();

    @NotEmpty
    private Set<String> actions = new LinkedHashSet<>();
}

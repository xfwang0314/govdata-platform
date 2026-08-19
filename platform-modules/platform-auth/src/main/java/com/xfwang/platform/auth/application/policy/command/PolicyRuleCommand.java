package com.xfwang.platform.auth.application.policy.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 一个策略组版本中的资源授权规则。 */
@Data
public class PolicyRuleCommand {

    private String ruleCode;

    @NotBlank
    private String resourceType;

    /** TYPE 表示同类型全部资源，INSTANCE 表示 targets 中的资源。 */
    @NotBlank
    private String resourceScope;

    @Valid
    private List<PolicyResourceTargetCommand> targets = new ArrayList<>();

    @NotEmpty
    private Set<String> actions = new LinkedHashSet<>();

    @NotEmpty
    private Set<String> purposes = new LinkedHashSet<>();

    private String conditionJson;

    private String obligationJson;
}

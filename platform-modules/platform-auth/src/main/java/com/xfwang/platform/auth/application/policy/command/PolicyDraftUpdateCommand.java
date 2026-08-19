package com.xfwang.platform.auth.application.policy.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 修改尚未发布的策略版本。 */
@Data
public class PolicyDraftUpdateCommand implements PolicyContentCommand {

    @NotNull
    @Min(0)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer versionLockVersion;

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

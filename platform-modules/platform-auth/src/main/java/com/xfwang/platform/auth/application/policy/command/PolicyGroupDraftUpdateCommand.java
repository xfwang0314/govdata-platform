package com.xfwang.platform.auth.application.policy.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 修改策略组唯一草稿。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PolicyGroupDraftUpdateCommand extends PolicyGroupDraftCommand {

    @NotNull
    @Min(0)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer versionLockVersion;
}

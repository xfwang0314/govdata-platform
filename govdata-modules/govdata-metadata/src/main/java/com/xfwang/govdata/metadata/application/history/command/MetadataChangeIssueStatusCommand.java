package com.xfwang.govdata.metadata.application.history.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 变更问题状态更新命令。 */
@Data
public class MetadataChangeIssueStatusCommand {
    @NotBlank
    private String issueStatus;
}

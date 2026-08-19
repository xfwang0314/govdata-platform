package com.xfwang.platform.auth.application.policy.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 策略主体绑定输入。 */
@Data
public class PolicySubjectCommand {

    @NotBlank
    private String subjectType;

    @NotBlank
    private String subjectId;

    /** 仅 DEPARTMENT 使用，是否包含下级部门。 */
    private Boolean includeChildren = false;
}

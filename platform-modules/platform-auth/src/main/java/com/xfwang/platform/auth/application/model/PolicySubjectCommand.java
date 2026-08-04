package com.xfwang.platform.auth.application.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 策略主体绑定输入。 */
@Data
public class PolicySubjectCommand {

    @NotBlank
    private String subjectType;

    @NotBlank
    private String subjectId;
}

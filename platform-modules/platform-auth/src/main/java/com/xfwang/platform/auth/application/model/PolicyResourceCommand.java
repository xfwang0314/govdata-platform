package com.xfwang.platform.auth.application.model;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/** 策略资源选择输入。 */
@Data
public class PolicyResourceCommand {

    private Long resourceId;
    private String resourceType;
    private Boolean includeChildren = Boolean.FALSE;

    @AssertTrue(message = "resourceId 与 resourceType 必须且只能填写一个")
    public boolean isSelectorValid() {
        return (resourceId == null) != (resourceType == null || resourceType.isBlank());
    }
}

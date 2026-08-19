package com.xfwang.platform.auth.application.grant.view;

import lombok.Builder;
import lombok.Value;

/** Grant 的资源动作范围。 */
@Value
@Builder
public class GrantScopeView {
    Long scopeId;
    Long resourceId;
    String resourceCode;
    String resourceName;
    String actionCode;
    Boolean includeChildren;
    String obligationJson;
}

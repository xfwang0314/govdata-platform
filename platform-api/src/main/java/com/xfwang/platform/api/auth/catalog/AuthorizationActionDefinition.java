package com.xfwang.platform.api.auth.catalog;

import java.io.Serial;
import java.io.Serializable;

/** 某类 Resource 支持的一个授权动作。 */
public record AuthorizationActionDefinition(String code, String label, String riskLevel)
    implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

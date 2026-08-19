package com.xfwang.platform.api.auth.catalog;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** 业务模块贡献的稳定授权用途及其适用资源类型。 */
public record AuthorizationPurposeDefinition(String code, String label,
                                             Set<String> resourceTypeCodes) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationPurposeDefinition {
        resourceTypeCodes = resourceTypeCodes == null ? Set.of() : Set.copyOf(resourceTypeCodes);
    }
}

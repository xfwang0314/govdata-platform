package com.xfwang.platform.api.auth.model;

import com.xfwang.platform.api.auth.enums.PrincipalType;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** Subject 属性贡献器可以依赖的中立身份快照。 */
public record SubjectAttributeRequest(String tenantId,
                                      PrincipalType principalType,
                                      String principalId,
                                      Long deptId,
                                      Set<Long> deptPathIds,
                                      Set<Long> roleIds,
                                      Set<String> roleKeys) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SubjectAttributeRequest {
        deptPathIds = deptPathIds == null ? Set.of() : Set.copyOf(deptPathIds);
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        roleKeys = roleKeys == null ? Set.of() : Set.copyOf(roleKeys);
    }
}

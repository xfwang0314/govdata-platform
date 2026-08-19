package com.xfwang.platform.api.auth.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** 当前主体对某类资源的批量可见范围。 */
public record AuthorizedResourceScope(boolean unrestricted, Set<Long> resourceIds) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizedResourceScope {
        resourceIds = resourceIds == null ? Set.of() : Set.copyOf(resourceIds);
        if (unrestricted && !resourceIds.isEmpty()) {
            throw new IllegalArgumentException("不受限资源范围不能同时携带资源ID");
        }
    }

    public static AuthorizedResourceScope unrestrictedScope() {
        return new AuthorizedResourceScope(true, Set.of());
    }

    public static AuthorizedResourceScope restricted(Set<Long> resourceIds) {
        return new AuthorizedResourceScope(false, resourceIds);
    }

    public boolean denied() {
        return !unrestricted && resourceIds.isEmpty();
    }
}

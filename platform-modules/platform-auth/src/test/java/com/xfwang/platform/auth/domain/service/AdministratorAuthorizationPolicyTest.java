package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.api.auth.model.SubjectContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdministratorAuthorizationPolicyTest {

    private final AdministratorAuthorizationPolicy policy = new AdministratorAuthorizationPolicy(1L, "admin");

    @Test
    void recognizesOnlySuperAdminAndTenantAdmin() {
        assertThat(policy.permitsTenantOperation(SubjectContext.builder()
            .userId(1L).roleKeys(Set.of()).build())).isTrue();
        assertThat(policy.permitsTenantOperation(SubjectContext.builder()
            .userId(10L).roleKeys(Set.of("admin")).build())).isTrue();
        assertThat(policy.permitsTenantOperation(SubjectContext.builder()
            .userId(10L).roleKeys(Set.of("ordinary")).build())).isFalse();
        assertThat(policy.permitsTenantOperation(null)).isFalse();
    }
}

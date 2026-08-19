package com.xfwang.platform.auth.infrastructure.enforcement;

import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.decision.RuntimePolicyRuleMatcher;
import com.xfwang.platform.auth.domain.service.AdministratorAuthorizationPolicy;
import com.xfwang.platform.auth.domain.service.DepartmentDataScopeConditionResolver;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import org.dromara.system.api.DeptService;
import org.dromara.system.api.permission.DataAuthorizationScope;
import org.dromara.system.api.permission.DataAuthorizationScopeRequest;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbacDataAuthorizationScopeProviderTest {

    @Test
    void superAdminMustReceiveUnrestrictedRoleManagementScope() {
        SubjectProvider subjectProvider = mock(SubjectProvider.class);
        when(subjectProvider.currentSubject()).thenReturn(SubjectContext.builder()
            .userId(1L)
            .roleKeys(Set.of("superadmin"))
            .build());
        AbacDataAuthorizationScopeProvider provider = new AbacDataAuthorizationScopeProvider(
            subjectProvider,
            mock(DeptService.class),
            mock(AuthzPolicyRuntimeRuleMapper.class),
            mock(AuthzPolicyRuntimeSubjectMapper.class),
            mock(AuthorizationJsonCodec.class),
            mock(DepartmentDataScopeConditionResolver.class),
            new AdministratorAuthorizationPolicy(1L, "admin"),
            mock(RuntimePolicyRuleMatcher.class));

        DataAuthorizationScope scope = provider.resolve(new DataAuthorizationScopeRequest(
                SystemAuthorizationResources.ROLE,
                SystemAuthorizationResources.VIEW,
                SystemAuthorizationResources.ROLE_ADMINISTRATION))
            .orElseThrow();

        assertThat(scope.unrestricted()).isTrue();
    }
}

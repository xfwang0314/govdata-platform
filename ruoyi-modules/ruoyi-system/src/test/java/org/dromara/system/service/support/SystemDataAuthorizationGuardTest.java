package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.api.permission.DataAuthorizationScope;
import org.dromara.system.api.permission.DataAuthorizationScopeProvider;
import org.dromara.system.api.permission.DataAuthorizationScopeRequest;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SystemDataAuthorizationGuardTest {

    @Test
    void enforcesTheRequestedMutationActionAgainstTheTargetDepartment() {
        DataAuthorizationScopeProvider provider = mock(DataAuthorizationScopeProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataAuthorizationScopeProvider> providerLookup = mock(ObjectProvider.class);
        when(providerLookup.getIfAvailable()).thenReturn(provider);
        DataAuthorizationScopeRequest request = new DataAuthorizationScopeRequest(
            SystemAuthorizationResources.USER, SystemAuthorizationResources.UPDATE,
            SystemAuthorizationResources.USER_ADMINISTRATION);
        when(provider.resolve(request)).thenReturn(Optional.of(
            DataAuthorizationScope.restricted(Set.of(101L), false)));
        SystemDataAuthorizationGuard guard = new SystemDataAuthorizationGuard(providerLookup);

        assertThatCode(() -> guard.check(request, 101L, 20L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.check(request, 102L, 21L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限");
        verify(provider, times(2)).resolve(request);
    }

    @Test
    void deniesWhenThePlatformAuthorizationProviderIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DataAuthorizationScopeProvider> providerLookup = mock(ObjectProvider.class);
        when(providerLookup.getIfAvailable()).thenReturn(null);
        SystemDataAuthorizationGuard guard = new SystemDataAuthorizationGuard(providerLookup);
        DataAuthorizationScopeRequest request = new DataAuthorizationScopeRequest(
            SystemAuthorizationResources.DEPARTMENT, SystemAuthorizationResources.DELETE,
            SystemAuthorizationResources.DEPARTMENT_ADMINISTRATION);

        assertThatThrownBy(() -> guard.check(request, 101L, null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限");
    }
}

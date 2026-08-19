package com.xfwang.govdata.datasource.application.authorization;

import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DataSourceAuthorizationScopeTest {

    @Test
    void resolvesAdministrationViewScopeWithDomainOwnedCodes() {
        AuthorizedResourceService authorization = mock(AuthorizedResourceService.class);
        ArgumentCaptor<AuthorizedResourceQuery> queryCaptor =
            ArgumentCaptor.forClass(AuthorizedResourceQuery.class);
        when(authorization.resolveScope(queryCaptor.capture()))
            .thenReturn(AuthorizedResourceScope.restricted(Set.of(10L)));

        var result = new DataSourceAuthorizationScope(authorization).administrationViewScope();

        assertThat(result.resourceIds()).containsExactly(10L);
        AuthorizedResourceQuery query = queryCaptor.getValue();
        assertThat(query.getResourceType()).isEqualTo(DataSourceAuthorizationCodes.RESOURCE_TYPE);
        assertThat(query.getAction()).isEqualTo(DataSourceAuthorizationCodes.Action.VIEW);
        assertThat(query.getPurpose()).isEqualTo(DataSourceAuthorizationCodes.Purpose.ADMINISTRATION);
        verify(authorization).resolveScope(query);
    }
}

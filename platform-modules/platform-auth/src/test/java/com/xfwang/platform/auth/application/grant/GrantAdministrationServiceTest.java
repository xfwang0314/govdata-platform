package com.xfwang.platform.auth.application.grant;

import com.xfwang.platform.auth.application.grant.command.GrantDraftCommand;
import com.xfwang.platform.auth.application.grant.command.GrantScopeCommand;
import com.xfwang.platform.auth.application.grant.view.GrantView;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class GrantAdministrationServiceTest {

    @Test
    void createsActivatesAndRevokesExplicitResourceActionGrant() {
        AuthzGrantMapper grantMapper = mock(AuthzGrantMapper.class);
        AuthzGrantScopeMapper scopeMapper = mock(AuthzGrantScopeMapper.class);
        ResourceRegistry resourceRegistry = mock(ResourceRegistry.class);
        AtomicReference<com.xfwang.platform.auth.domain.entity.AuthzGrant> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            com.xfwang.platform.auth.domain.entity.AuthzGrant value = invocation.getArgument(0);
            value.setGrantId(401L);
            stored.set(value);
            return 1;
        }).when(grantMapper).insert(any(com.xfwang.platform.auth.domain.entity.AuthzGrant.class));
        when(grantMapper.selectById(401L)).thenAnswer(invocation -> stored.get());
        when(scopeMapper.selectCount(any())).thenReturn(1L);
        when(resourceRegistry.findById(301L)).thenReturn(Optional.of(
            ResourceDescriptor.builder().resourceId(301L).build()));

        GrantAdministrationService service = new DefaultGrantAdministrationService(
            grantMapper, scopeMapper, new AuthorizationJsonCodec(new ObjectMapper()), resourceRegistry);
        GrantScopeCommand scope = new GrantScopeCommand();
        scope.setResourceId(301L);
        scope.setActionCode("QUERY");

        GrantDraftCommand command = new GrantDraftCommand();
        command.setSourceType("ADMIN");
        command.setSourceId("MANUAL-0001");
        command.setGrantorType("DEPARTMENT");
        command.setGrantorId("103");
        command.setGranteeType("HUMAN");
        command.setGranteeId("10");
        command.setPurpose("人口数据核验");
        command.setEffectiveTime(LocalDateTime.of(2026, 8, 2, 0, 0));
        command.setScopes(List.of(scope));

        GrantView view = service.createDraft(command);

        assertThat(view.getGrantId()).isEqualTo(401L);
        assertThat(view.getGrantCode()).startsWith("grt_");
        assertThat(view.getStatus()).isEqualTo("DRAFT");
        assertThat(view.getPurpose()).isEqualTo("人口数据核验");

        GrantView active = service.activate(401L);
        assertThat(active.getStatus()).isEqualTo("ACTIVE");

        GrantView revoked = service.revoke(401L, "共享申请已撤销");
        assertThat(revoked.getStatus()).isEqualTo("REVOKED");
        assertThat(stored.get().getRevokeReason()).isEqualTo("共享申请已撤销");
    }

    @Test
    void rejectsAgentGrantUntilDelegationDecisionPathIsEnabled() {
        AuthzGrantMapper grantMapper = mock(AuthzGrantMapper.class);
        AuthzGrantScopeMapper scopeMapper = mock(AuthzGrantScopeMapper.class);
        ResourceRegistry resourceRegistry = mock(ResourceRegistry.class);
        GrantAdministrationService service = new DefaultGrantAdministrationService(
            grantMapper, scopeMapper, new AuthorizationJsonCodec(new ObjectMapper()), resourceRegistry);

        GrantScopeCommand scope = new GrantScopeCommand();
        scope.setResourceId(301L);
        scope.setActionCode("QUERY");
        GrantDraftCommand command = new GrantDraftCommand();
        command.setSourceType("ADMIN");
        command.setSourceId("MANUAL-AGENT-0001");
        command.setGrantorType("HUMAN");
        command.setGrantorId("10");
        command.setGranteeType("AGENT");
        command.setGranteeId("9001");
        command.setPurpose("元数据查询");
        command.setEffectiveTime(LocalDateTime.of(2026, 8, 3, 0, 0));
        command.setScopes(List.of(scope));

        assertThatThrownBy(() -> service.createDraft(command))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("尚未启用");
    }
}

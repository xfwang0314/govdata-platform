package com.xfwang.platform.auth.application.grant;

import com.xfwang.platform.auth.application.grant.command.GrantDraftCommand;
import com.xfwang.platform.auth.application.grant.command.GrantScopeCommand;
import com.xfwang.platform.auth.application.grant.view.GrantView;
import com.xfwang.platform.api.auth.catalog.AuthorizationPurposeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.catalog.ResourceSelectionMode;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        AuthorizationCatalog authorizationCatalog = mock(AuthorizationCatalog.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SubjectProvider subjectProvider = mock(SubjectProvider.class);
        when(subjectProvider.currentSubject()).thenReturn(SubjectContext.builder()
            .userId(99L).deptId(88L).build());
        AtomicReference<com.xfwang.platform.auth.domain.entity.AuthzGrantScope> storedScope =
            new AtomicReference<>();
        AtomicReference<com.xfwang.platform.auth.domain.entity.AuthzGrant> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            com.xfwang.platform.auth.domain.entity.AuthzGrant value = invocation.getArgument(0);
            value.setGrantId(401L);
            stored.set(value);
            return 1;
        }).when(grantMapper).insert(any(com.xfwang.platform.auth.domain.entity.AuthzGrant.class));
        when(grantMapper.selectById(401L)).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            storedScope.set(invocation.getArgument(0));
            return 1;
        }).when(scopeMapper).insert(any(com.xfwang.platform.auth.domain.entity.AuthzGrantScope.class));
        when(scopeMapper.selectList(any())).thenAnswer(invocation -> List.of(storedScope.get()));
        when(resourceRegistry.findById(301L)).thenReturn(Optional.of(
            ResourceDescriptor.builder().resourceId(301L).resourceType("test:resource").build()));
        when(authorizationCatalog.findPurpose("DATASOURCE_ADMINISTRATION"))
            .thenReturn(Optional.of(new AuthorizationPurposeDefinition(
                "DATASOURCE_ADMINISTRATION", "数据源管理", Set.of("test:resource"))));
        when(authorizationCatalog.supportsAction("test:resource", "QUERY")).thenReturn(true);
        when(authorizationCatalog.supportsPurpose("test:resource", "DATASOURCE_ADMINISTRATION"))
            .thenReturn(true);
        when(authorizationCatalog.findResourceType("test:resource")).thenReturn(Optional.of(
            new ResourceTypeDefinition("test", "测试域", "test", "test:resource", "测试资源",
                Set.of(), Set.of(ResourceSelectionMode.INSTANCE),
                Set.of(ResourceAuthorizationCapability.GRANT), List.of(), List.of())));
        when(authorizationService.decide(any())).thenReturn(
            AccessDecision.builder().decision(DecisionType.PERMIT).build());

        GrantAdministrationService service = new DefaultGrantAdministrationService(
            grantMapper, scopeMapper, new AuthorizationJsonCodec(new ObjectMapper()), resourceRegistry,
            authorizationCatalog, authorizationService, subjectProvider);
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
        command.setPurpose("DATASOURCE_ADMINISTRATION");
        command.setEffectiveTime(LocalDateTime.of(2026, 8, 2, 0, 0));
        command.setScopes(List.of(scope));

        GrantView view = service.createDraft(command);

        assertThat(view.getGrantId()).isEqualTo(401L);
        assertThat(view.getGrantCode()).startsWith("grt_");
        assertThat(view.getStatus()).isEqualTo("DRAFT");
        assertThat(view.getPurpose()).isEqualTo("DATASOURCE_ADMINISTRATION");
        assertThat(stored.get().getCreateBy()).isEqualTo(99L);
        assertThat(storedScope.get().getCreateBy()).isEqualTo(99L);

        GrantView active = service.activate(401L);
        assertThat(active.getStatus()).isEqualTo("ACTIVE");

        GrantView revoked = service.revoke(401L, "共享申请已撤销");
        assertThat(revoked.getStatus()).isEqualTo("REVOKED");
        assertThat(stored.get().getRevokeReason()).isEqualTo("共享申请已撤销");
        assertThat(stored.get().getRevokedBy()).isEqualTo(99L);
    }

    @Test
    void rejectsAgentGrantUntilDelegationDecisionPathIsEnabled() {
        AuthzGrantMapper grantMapper = mock(AuthzGrantMapper.class);
        AuthzGrantScopeMapper scopeMapper = mock(AuthzGrantScopeMapper.class);
        ResourceRegistry resourceRegistry = mock(ResourceRegistry.class);
        SubjectProvider subjectProvider = mock(SubjectProvider.class);
        GrantAdministrationService service = new DefaultGrantAdministrationService(
            grantMapper, scopeMapper, new AuthorizationJsonCodec(new ObjectMapper()), resourceRegistry,
            mock(AuthorizationCatalog.class), mock(AuthorizationService.class), subjectProvider);

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

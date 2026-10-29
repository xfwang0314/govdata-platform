package com.xfwang.platform.mcp.application.service;

import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.mcp.enums.McpRiskLevel;
import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.model.McpModuleDefinition;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import com.xfwang.platform.api.mcp.service.McpToolContributor;
import com.xfwang.platform.mcp.domain.entity.AuthzAgentToken;
import com.xfwang.platform.mcp.domain.exception.McpErrorCode;
import com.xfwang.platform.mcp.infrastructure.security.McpSubjectContextHolder;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolInvocationServiceTest {

    @Test
    void invokesToolWithAgentSubjectAndClearsContext() {
        AgentTokenAuthenticator authenticator = mock(AgentTokenAuthenticator.class);
        McpSubjectContextHolder holder = new McpSubjectContextHolder();
        McpToolContributor contributor = contributor(McpRiskLevel.LOW);
        SubjectContext subject = SubjectContext.builder()
            .tenantId("103")
            .principalType(PrincipalType.AGENT)
            .principalId("agt_test")
            .userId(1L)
            .build();
        AuthzAgentToken token = new AuthzAgentToken();
        token.setTenantId("103");
        when(authenticator.authenticate("Bearer test")).thenReturn(
            new AgentTokenAuthenticator.AuthenticatedAgentContext(
                token, subject, Set.of("DATASOURCE")));
        when(contributor.execute(eq("datasource.test"), any(), eq(subject)))
            .thenAnswer(invocation -> {
                assertEquals(subject, holder.currentSubject().orElseThrow());
                return McpCallResponse.success("ok");
            });
        McpToolInvocationService service = new McpToolInvocationService(
            List.of(contributor), authenticator, holder);
        service.initializeRegistry();

        McpCallResponse response = service.invoke("Bearer test", "datasource.test", Map.of());

        assertTrue(response.getContent().get(0).getText().contains("ok"));
        assertTrue(holder.currentSubject().isEmpty());
        verify(authenticator).checkInvocationQuota(token);
    }

    @Test
    void rejectsHighRiskToolWithoutExplicitConfirmation() {
        AgentTokenAuthenticator authenticator = mock(AgentTokenAuthenticator.class);
        McpToolContributor contributor = contributor(McpRiskLevel.HIGH);
        AuthzAgentToken token = new AuthzAgentToken();
        token.setTenantId("103");
        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        when(authenticator.authenticate("Bearer test")).thenReturn(
            new AgentTokenAuthenticator.AuthenticatedAgentContext(
                token, subject, Set.of("DATASOURCE")));
        McpToolInvocationService service = new McpToolInvocationService(
            List.of(contributor), authenticator, new McpSubjectContextHolder());
        service.initializeRegistry();

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.invoke("Bearer test", "datasource.test", Map.of()));

        assertEquals(McpErrorCode.HIGH_RISK_CONFIRMATION_REQUIRED.getCode(), exception.getErrorCode());
    }

    private McpToolContributor contributor(McpRiskLevel riskLevel) {
        McpToolContributor contributor = mock(McpToolContributor.class);
        when(contributor.module()).thenReturn(new McpModuleDefinition("DATASOURCE", "数据源管理"));
        when(contributor.tools()).thenReturn(List.of(McpToolDefinition.builder()
            .name("datasource.test")
            .moduleCode("DATASOURCE")
            .description("测试工具")
            .inputSchema(Map.of("type", "object", "properties", Map.of()))
            .riskLevel(riskLevel)
            .build()));
        return contributor;
    }
}

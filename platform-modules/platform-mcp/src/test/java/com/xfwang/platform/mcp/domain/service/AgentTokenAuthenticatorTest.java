package com.xfwang.platform.mcp.domain.service;

import com.xfwang.platform.mcp.application.service.AgentTokenAuthenticator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.mcp.domain.entity.AuthzAgentToken;
import com.xfwang.platform.mcp.domain.exception.McpErrorCode;
import com.xfwang.platform.mcp.infrastructure.persistence.mapper.AuthzAgentTokenMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTokenAuthenticatorTest {

    @Mock
    private AuthzAgentTokenMapper tokenMapper;

    @Mock
    private McpRateLimiter rateLimiter;

    @Mock
    private SubjectProvider subjectProvider;

    private AgentTokenGenerator tokenGenerator;
    private AgentTokenAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        tokenGenerator = new AgentTokenGenerator();
        authenticator = new AgentTokenAuthenticator(tokenMapper, tokenGenerator, rateLimiter, subjectProvider);
    }

    @Test
    void testAuthenticateSuccess() {
        String rawToken = "mcp_sk_live_test123456";
        String tokenHash = tokenGenerator.hash(rawToken);

        AuthzAgentToken mockToken = new AuthzAgentToken();
        mockToken.setTokenId(1L);
        mockToken.setAgentName("测试Agent");
        mockToken.setAgentCode("agt_test");
        mockToken.setTokenHash(tokenHash);
        mockToken.setUserId(1001L);
        mockToken.setDeptId(2001L);
        mockToken.setTenantId("103");
        mockToken.setStatus("ACTIVE");
        mockToken.setAllowedCategories("DATASOURCE,STANDARD");
        mockToken.setExpireTime(LocalDateTime.now().plusDays(10));

        when(tokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockToken);
        when(subjectProvider.subjectForUser("103", 1001L)).thenReturn(SubjectContext.builder()
            .tenantId("103")
            .principalType(PrincipalType.HUMAN)
            .principalId("1001")
            .userId(1001L)
            .deptId(2001L)
            .deptPathIds(Set.of(20L, 2001L))
            .roleIds(Set.of(7L))
            .roleKeys(Set.of("data_steward"))
            .attributes(Map.of("securityLevel", 3))
            .build());
        AgentTokenAuthenticator.AuthenticatedAgentContext context =
            authenticator.authenticate("Bearer " + rawToken);

        assertNotNull(context);
        assertEquals(mockToken, context.token());
        assertEquals(PrincipalType.AGENT, context.subjectContext().getPrincipalType());
        assertEquals("agt_test", context.subjectContext().getPrincipalId());
        assertEquals(1001L, context.subjectContext().getUserId());
        assertEquals(1001L, context.subjectContext().getAttributes().get("ownerUserId"));
        assertEquals(Set.of(20L, 2001L), context.subjectContext().getDeptPathIds());
        assertEquals(Set.of(7L), context.subjectContext().getRoleIds());
        assertEquals(Set.of("data_steward"), context.subjectContext().getRoleKeys());
        assertEquals(3, context.subjectContext().getAttributes().get("securityLevel"));
        assertTrue(context.allowedCategories().contains("DATASOURCE"));
        assertTrue(context.allowedCategories().contains("STANDARD"));
    }

    @Test
    void testInvocationQuotaIsCheckedSeparately() {
        AuthzAgentToken token = new AuthzAgentToken();
        when(rateLimiter.checkRateLimit(token))
            .thenReturn(new McpRateLimiter.RateLimitResult(false, "配额已耗尽"));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> authenticator.checkInvocationQuota(token));

        assertEquals(McpErrorCode.RATE_LIMIT_EXCEEDED.getCode(), exception.getErrorCode());
    }

    @Test
    void testAuthenticateMissingToken() {
        assertThrows(ServiceException.class, () -> authenticator.authenticate(null));
        assertThrows(ServiceException.class, () -> authenticator.authenticate(""));
    }

    @Test
    void testAuthenticateRevokedToken() {
        String rawToken = "mcp_sk_live_test123456";

        AuthzAgentToken mockToken = new AuthzAgentToken();
        mockToken.setStatus("REVOKED");

        when(tokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockToken);

        ServiceException ex = assertThrows(ServiceException.class, () -> authenticator.authenticate("Bearer " + rawToken));
        assertEquals(McpErrorCode.TOKEN_INACTIVE.getCode(), ex.getErrorCode());
    }

    @Test
    void testAuthenticateExpiredToken() {
        String rawToken = "mcp_sk_live_test123456";

        AuthzAgentToken mockToken = new AuthzAgentToken();
        mockToken.setStatus("ACTIVE");
        mockToken.setExpireTime(LocalDateTime.now().minusDays(1));

        when(tokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockToken);

        ServiceException ex = assertThrows(ServiceException.class, () -> authenticator.authenticate("Bearer " + rawToken));
        assertEquals(McpErrorCode.TOKEN_EXPIRED.getCode(), ex.getErrorCode());
    }
}

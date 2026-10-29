package com.xfwang.platform.mcp.application.service;

import com.xfwang.platform.mcp.application.token.view.AgentTokenIssuedView;
import com.xfwang.platform.mcp.domain.entity.AuthzAgentToken;
import com.xfwang.platform.mcp.domain.service.AgentTokenGenerator;
import com.xfwang.platform.mcp.infrastructure.persistence.mapper.AuthzAgentTokenMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTokenApplicationServiceTest {

    @Mock
    private AuthzAgentTokenMapper tokenMapper;

    private AgentTokenGenerator tokenGenerator;
    private AgentTokenApplicationService service;

    @BeforeEach
    void setUp() {
        tokenGenerator = new AgentTokenGenerator();
        service = new AgentTokenApplicationService(tokenMapper, tokenGenerator, List.of());
    }

    @Test
    void rotateReplacesSecretAndReturnsOnlyGenericClientConfig() {
        AuthzAgentToken token = accessibleToken("ACTIVE");
        token.setTokenHash("old-hash");
        token.setRotationCount(2);
        when(tokenMapper.selectById(1L)).thenReturn(token);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1001L);

            AgentTokenIssuedView issued = service.rotateToken(1L, "http://localhost:8080/platform/mcp");

            assertNotEquals("old-hash", token.getTokenHash());
            assertEquals(3, token.getRotationCount());
            assertTrue(issued.getClientConfigJson().contains("Authorization"));
            assertTrue(issued.getClientConfigJson().contains("Bearer " + issued.getPlainToken()));
            verify(tokenMapper).updateById(token);
        }
    }

    @Test
    void revokedCredentialCannotBeRotated() {
        when(tokenMapper.selectById(1L)).thenReturn(accessibleToken("REVOKED"));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1001L);
            assertThrows(ServiceException.class,
                () -> service.rotateToken(1L, "http://localhost:8080/platform/mcp"));
        }
    }

    @Test
    void expiredDisabledCredentialCannotBeEnabled() {
        AuthzAgentToken token = accessibleToken("DISABLED");
        token.setExpireTime(LocalDateTime.now().minusMinutes(1));
        when(tokenMapper.selectById(1L)).thenReturn(token);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1001L);
            assertThrows(ServiceException.class, () -> service.enableToken(1L));
        }
    }

    private AuthzAgentToken accessibleToken(String status) {
        AuthzAgentToken token = new AuthzAgentToken();
        token.setTokenId(1L);
        token.setUserId(1001L);
        token.setAgentName("测试 Agent");
        token.setAgentCode("agt_test");
        token.setTokenPrefix("mcp_sk_live_test");
        token.setAllowedCategories("DATASOURCE");
        token.setStatus(status);
        token.setMaxDailyRequests(2000);
        token.setUsedRequestsToday(0);
        token.setRotationCount(0);
        return token;
    }
}

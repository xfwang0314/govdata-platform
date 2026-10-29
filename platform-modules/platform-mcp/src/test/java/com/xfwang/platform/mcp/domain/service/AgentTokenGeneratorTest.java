package com.xfwang.platform.mcp.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentTokenGeneratorTest {

    private final AgentTokenGenerator generator = new AgentTokenGenerator();

    @Test
    void testGenerateToken() {
        AgentTokenGenerator.GeneratedToken token = generator.generate();

        assertNotNull(token);
        assertNotNull(token.plainToken());
        assertTrue(token.plainToken().startsWith("mcp_sk_live_"));
        assertNotNull(token.tokenHash());
        assertEquals(64, token.tokenHash().length()); // SHA-256 hex is 64 chars
        assertTrue(token.tokenPrefix().startsWith("mcp_sk_live_"));
        assertEquals(generator.hash(token.plainToken()), token.tokenHash());
    }

    @Test
    void testFormatClientConfig() {
        String streamableUrl = "http://localhost:8080/platform/mcp";
        String plainToken = "mcp_sk_live_123456";
        String config = generator.formatClientConfig(streamableUrl, plainToken);

        assertTrue(config.contains("mcpServers"));
        assertTrue(config.contains("govdata-platform"));
        assertTrue(config.contains(streamableUrl));
        assertFalse(config.contains("/sse"));
        assertTrue(config.contains("Bearer " + plainToken));
    }

}

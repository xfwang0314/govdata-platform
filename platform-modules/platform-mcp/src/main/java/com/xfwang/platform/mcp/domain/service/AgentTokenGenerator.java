package com.xfwang.platform.mcp.domain.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Agent 访问凭据生成器。
 */
@Component
public class AgentTokenGenerator {

    private static final String TOKEN_PREFIX = "mcp_sk_live_";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record GeneratedToken(String plainToken, String tokenHash, String tokenPrefix) {}

    /**
     * 生成高强度安全随机 Token 及其哈希。
     */
    public GeneratedToken generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String randomHex = HexFormat.of().formatHex(bytes);
        String plainToken = TOKEN_PREFIX + randomHex;
        String tokenHash = DigestUtil.sha256Hex(plainToken);
        String prefix = plainToken.substring(0, Math.min(plainToken.length(), 18));
        return new GeneratedToken(plainToken, tokenHash, prefix);
    }

    /**
     * 计算输入 Token 的 SHA-256 哈希。
     */
    public String hash(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return "";
        }
        return DigestUtil.sha256Hex(plainToken.trim());
    }

    /**
     * 生成标准 MCP 客户端 JSON 配置片段。
     */
    public String formatClientConfig(String streamableHttpUrl, String plainToken) {
        return """
            {
              "mcpServers": {
                "govdata-platform": {
                  "url": "%s",
                  "headers": {
                    "Authorization": "Bearer %s"
                  }
                }
              }
            }
            """.formatted(streamableHttpUrl, plainToken).trim();
    }

}

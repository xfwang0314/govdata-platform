package com.xfwang.platform.mcp.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.mcp.domain.entity.AuthzAgentToken;
import com.xfwang.platform.mcp.domain.exception.McpErrorCode;
import com.xfwang.platform.mcp.domain.model.AgentTokenStatus;
import com.xfwang.platform.mcp.domain.service.AgentTokenGenerator;
import com.xfwang.platform.mcp.domain.service.McpRateLimiter;
import com.xfwang.platform.mcp.infrastructure.persistence.mapper.AuthzAgentTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 凭据认证与双主体上下文构建器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTokenAuthenticator {

    private final AuthzAgentTokenMapper tokenMapper;
    private final AgentTokenGenerator tokenGenerator;
    private final McpRateLimiter rateLimiter;
    private final SubjectProvider subjectProvider;

    public record AuthenticatedAgentContext(
        AuthzAgentToken token,
        SubjectContext subjectContext,
        Set<String> allowedCategories
    ) {}

    /**
     * 校验 Token 凭据并构建鉴权上下文。
     *
     * @param tokenHeader 格式如 "Bearer mcp_sk_live_..." 或裸 Token 字符串
     * @return 经认证的 Agent 上下文
     */
    public AuthenticatedAgentContext authenticate(String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new ServiceException(McpErrorCode.TOKEN_MISSING);
        }

        String plainToken = tokenHeader.trim();
        if (plainToken.startsWith("Bearer ") || plainToken.startsWith("bearer ")) {
            plainToken = plainToken.substring(7).trim();
        }

        if (plainToken.isBlank()) {
            throw new ServiceException(McpErrorCode.TOKEN_INVALID);
        }

        String tokenHash = tokenGenerator.hash(plainToken);
        // Token 哈希全局唯一。认证阶段必须显式忽略租户插件，认证成功后再切入凭据所属租户。
        AuthzAgentToken token = TenantHelper.ignore(() -> tokenMapper.selectOne(
            new LambdaQueryWrapper<AuthzAgentToken>().eq(AuthzAgentToken::getTokenHash, tokenHash)));

        if (token == null) {
            throw new ServiceException(McpErrorCode.TOKEN_INVALID);
        }

        if (AgentTokenStatus.fromCode(token.getStatus()) != AgentTokenStatus.ACTIVE) {
            throw new ServiceException(McpErrorCode.TOKEN_INACTIVE);
        }

        if (token.getExpireTime() != null && LocalDateTime.now().isAfter(token.getExpireTime())) {
            throw new ServiceException(McpErrorCode.TOKEN_EXPIRED);
        }

        if (token.getTenantId() == null || token.getTenantId().isBlank()) {
            throw new ServiceException(McpErrorCode.TOKEN_TENANT_INVALID);
        }

        // 解析允许的类别集合
        Set<String> categories = new HashSet<>();
        if (token.getAllowedCategories() != null && !token.getAllowedCategories().isBlank()) {
            Arrays.stream(token.getAllowedCategories().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(categories::add);
        }

        SubjectContext owner = subjectProvider.subjectForUser(token.getTenantId(), token.getUserId());
        Map<String, Object> attributes = new HashMap<>(owner.getAttributes() == null
            ? Map.of() : owner.getAttributes());
        attributes.put("agentName", token.getAgentName());
        attributes.put("agentCode", token.getAgentCode());
        attributes.put("ownerUserId", owner.getUserId());
        attributes.put("ownerDeptId", owner.getDeptId());

        // OWNER_INHERIT：Agent 保持独立主体身份，但动态继承归属用户的当前授权属性。
        SubjectContext subjectContext = SubjectContext.builder()
            .tenantId(token.getTenantId())
            .principalType(PrincipalType.AGENT)
            .principalId(token.getAgentCode())
            .userId(owner.getUserId())
            .deptId(owner.getDeptId())
            .deptPathIds(owner.getDeptPathIds())
            .roleIds(owner.getRoleIds())
            .roleKeys(owner.getRoleKeys())
            .attributes(Map.copyOf(attributes))
            .build();

        return new AuthenticatedAgentContext(token, subjectContext, categories);
    }

    /** 仅在真正执行 tools/call 时消费配额。 */
    public void checkInvocationQuota(AuthzAgentToken token) {
        McpRateLimiter.RateLimitResult limitResult = rateLimiter.checkRateLimit(token);
        if (!limitResult.allowed()) {
            throw new ServiceException(McpErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }
}

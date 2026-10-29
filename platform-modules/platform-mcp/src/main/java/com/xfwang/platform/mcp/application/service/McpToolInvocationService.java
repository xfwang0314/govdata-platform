package com.xfwang.platform.mcp.application.service;

import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.enums.McpRiskLevel;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import com.xfwang.platform.api.mcp.service.McpToolContributor;
import com.xfwang.platform.mcp.domain.exception.McpErrorCode;
import com.xfwang.platform.mcp.infrastructure.security.McpSubjectContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** MCP 工具注册与调用的唯一应用入口。 */
@Service
@RequiredArgsConstructor
public class McpToolInvocationService {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");

    private final List<McpToolContributor> contributors;
    private final AgentTokenAuthenticator authenticator;
    private final McpSubjectContextHolder subjectContextHolder;
    private Map<String, RegisteredTool> tools = Map.of();

    @PostConstruct
    void initializeRegistry() {
        Map<String, RegisteredTool> registered = new LinkedHashMap<>();
        for (McpToolContributor contributor : contributors) {
            if (contributor.tools() == null) {
                continue;
            }
            for (McpToolDefinition definition : contributor.tools()) {
                if (definition.getName() == null
                    || !TOOL_NAME_PATTERN.matcher(definition.getName()).matches()) {
                    throw new IllegalStateException("MCP 工具名称不符合协议规范: " + definition.getName());
                }
                if (!contributor.module().code().equals(definition.getModuleCode())) {
                    throw new IllegalStateException("MCP 工具模块编码不一致: " + definition.getName());
                }
                RegisteredTool previous = registered.putIfAbsent(
                    definition.getName(), new RegisteredTool(definition, contributor));
                if (previous != null) {
                    throw new IllegalStateException("MCP 工具名称重复: " + definition.getName());
                }
            }
        }
        tools = Map.copyOf(registered);
    }

    public List<McpToolDefinition> definitions() {
        return tools.values().stream().map(RegisteredTool::definition).toList();
    }

    public void validateCredential(String authorizationHeader) {
        authenticator.authenticate(authorizationHeader);
    }

    public McpCallResponse invoke(String authorizationHeader, String toolName,
                                  Map<String, Object> arguments) {
        AgentTokenAuthenticator.AuthenticatedAgentContext context =
            authenticator.authenticate(authorizationHeader);
        RegisteredTool registered = tools.get(toolName);
        if (registered == null) {
            throw new ServiceException(McpErrorCode.TOOL_NOT_FOUND);
        }
        String moduleCode = registered.contributor().module().code();
        if (!context.allowedCategories().isEmpty()
            && !context.allowedCategories().contains(moduleCode)) {
            throw new ServiceException(McpErrorCode.TOOL_MODULE_FORBIDDEN);
        }
        if (registered.definition().getRiskLevel() == McpRiskLevel.HIGH
            && !Boolean.TRUE.equals(arguments == null ? null : arguments.get("_confirm_high_risk"))) {
            throw new ServiceException(McpErrorCode.HIGH_RISK_CONFIRMATION_REQUIRED);
        }
        authenticator.checkInvocationQuota(context.token());
        return TenantHelper.dynamic(context.subjectContext().getTenantId(), () ->
            subjectContextHolder.runAs(context.subjectContext(), () ->
                registered.contributor().execute(toolName,
                    arguments == null ? Map.of() : Map.copyOf(arguments),
                    context.subjectContext())));
    }

    private record RegisteredTool(McpToolDefinition definition, McpToolContributor contributor) {
    }
}

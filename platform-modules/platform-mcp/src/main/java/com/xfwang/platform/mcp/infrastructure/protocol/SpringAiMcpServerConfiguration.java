package com.xfwang.platform.mcp.infrastructure.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.api.mcp.enums.McpRiskLevel;
import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.model.McpContentItem;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import com.xfwang.platform.mcp.application.service.McpToolInvocationService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Spring AI / 官方 MCP Java SDK 的 Streamable HTTP 适配配置。 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SpringAiMcpServerConfiguration {

    private static final String AUTHORIZATION_CONTEXT_KEY = "govdata.authorization";
    private final McpToolInvocationService invocationService;
    private final ObjectMapper objectMapper;

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
        @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/platform/mcp}") String endpoint) {
        ServerTransportSecurityValidator validator = headers -> {
            String authorization = firstHeader(headers, "authorization");
            try {
                invocationService.validateCredential(authorization);
            } catch (RuntimeException exception) {
                throw new ServerTransportSecurityException(401, "Invalid MCP bearer credential");
            }
            validateOrigin(headers);
        };
        return WebMvcStreamableServerTransportProvider.builder()
            .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
            .mcpEndpoint(endpoint)
            .keepAliveInterval(Duration.ofSeconds(30))
            .securityValidator(validator)
            .contextExtractor(request -> McpTransportContext.create(Map.of(
                AUTHORIZATION_CONTEXT_KEY,
                request.headers().firstHeader("Authorization") == null
                    ? "" : request.headers().firstHeader("Authorization"))))
            .build();
    }

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> mcpToolSpecifications() {
        return invocationService.definitions().stream().map(this::toSpecification).toList();
    }

    private McpServerFeatures.SyncToolSpecification toSpecification(McpToolDefinition definition) {
        Map<String, Object> schema = definition.getInputSchema() == null
            ? Map.of() : definition.getInputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object", properties, required, false, Map.of(), Map.of());
        boolean readOnly = definition.getRiskLevel() != McpRiskLevel.HIGH;
        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(definition.getName())
            .description(definition.getDescription())
            .inputSchema(inputSchema)
            .outputSchema(definition.getOutputSchema())
            .annotations(new McpSchema.ToolAnnotations(
                definition.getName(), readOnly, !readOnly, readOnly, false, false))
            .meta(Map.of(
                "govdata/module", definition.getModuleCode(),
                "govdata/riskLevel", definition.getRiskLevel().name()))
            .build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, arguments) -> {
            String authorization = String.valueOf(exchange.transportContext().get(AUTHORIZATION_CONTEXT_KEY));
            try {
                McpCallResponse response = invocationService.invoke(
                    authorization, definition.getName(), arguments);
                List<McpSchema.Content> content = response.getContent().stream()
                    .map(McpContentItem::getText)
                    .map(McpSchema.TextContent::new)
                    .map(item -> (McpSchema.Content) item)
                    .toList();
                return McpSchema.CallToolResult.builder()
                    .content(content)
                    .isError(response.isError())
                    .build();
            } catch (RuntimeException exception) {
                String traceId = MDC.get("traceId");
                log.error("MCP 工具调用失败, tool={}, traceId={}",
                    definition.getName(), traceId, exception);
                String message = traceId == null || traceId.isBlank()
                    ? "工具调用失败，请联系管理员"
                    : "工具调用失败，traceId=" + traceId;
                return McpSchema.CallToolResult.builder()
                    .addTextContent(message)
                    .isError(true)
                    .build();
            }
        });
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .flatMap(entry -> entry.getValue().stream())
            .findFirst()
            .orElse(null);
    }

    private static void validateOrigin(Map<String, List<String>> headers)
        throws ServerTransportSecurityException {
        String origin = firstHeader(headers, "origin");
        String host = firstHeader(headers, "host");
        if (origin == null || origin.isBlank() || host == null || host.isBlank()) {
            return;
        }
        try {
            String originHost = java.net.URI.create(origin).getAuthority();
            if (originHost != null && !originHost.equalsIgnoreCase(host)) {
                throw new ServerTransportSecurityException(403, "MCP Origin is not allowed");
            }
        } catch (IllegalArgumentException exception) {
            throw new ServerTransportSecurityException(403, "Invalid MCP Origin");
        }
    }
}

package com.xfwang.platform.mcp.interfaces.controller;

import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import com.xfwang.platform.api.mcp.service.McpToolContributor;
import com.xfwang.platform.mcp.application.option.view.McpOptionsView;
import com.xfwang.platform.mcp.domain.model.AgentTokenStatus;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 网关与凭据选项字典接口控制器。
 */
@RestController
@RequestMapping("/platform/mcp/options")
@RequiredArgsConstructor
public class McpOptionsController {

    private final List<McpToolContributor> toolContributors;

    @GetMapping
    @SaCheckPermission("platform:mcp:agent:query")
    public R<McpOptionsView> getOptions() {
        Map<String, McpOptionsView.CategoryOptionItem> categoriesByCode = new LinkedHashMap<>();
        toolContributors.stream()
            .sorted(java.util.Comparator.comparing(contributor -> contributor.module().code()))
            .forEach(contributor -> {
                List<McpToolDefinition> tools = contributor.tools() == null
                    ? Collections.emptyList() : contributor.tools();
                List<McpOptionsView.ToolSummaryItem> summaries = tools.stream()
                    .map(tool -> McpOptionsView.ToolSummaryItem.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .riskLevel(tool.getRiskLevel().name())
                    .inputSchema(tool.getInputSchema() != null ? tool.getInputSchema() : Collections.emptyMap())
                    .outputSchema(tool.getOutputSchema() != null ? tool.getOutputSchema() : Collections.emptyMap())
                    .build())
                    .toList();
                categoriesByCode.compute(contributor.module().code(), (code, current) ->
                    McpOptionsView.CategoryOptionItem.builder()
                        .label(contributor.module().label())
                        .value(code)
                        .code(code)
                        .tools(java.util.stream.Stream.concat(
                            current == null ? java.util.stream.Stream.empty() : current.getTools().stream(),
                            summaries.stream()).toList())
                        .build());
            });

        List<McpOptionsView.OptionItem> statuses = java.util.Arrays.stream(AgentTokenStatus.values())
            .map(status -> McpOptionsView.OptionItem.builder()
                .label(status.getLabel()).value(status.getCode()).build())
            .toList();

        return R.ok(McpOptionsView.builder()
            .categories(List.copyOf(categoriesByCode.values()))
            .statuses(statuses)
            .primaryTransport("STREAMABLE_HTTP")
            .build());
    }
}

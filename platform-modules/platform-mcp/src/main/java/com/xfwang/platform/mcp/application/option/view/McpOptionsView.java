package com.xfwang.platform.mcp.application.option.view;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** MCP 管理页面选项视图。 */
@Data
@Builder
public class McpOptionsView {
    private List<CategoryOptionItem> categories;
    private List<OptionItem> statuses;
    private String primaryTransport;

    @Data
    @Builder
    public static class CategoryOptionItem {
        private String label;
        private String value;
        private String code;
        private List<ToolSummaryItem> tools;
    }

    @Data
    @Builder
    public static class ToolSummaryItem {
        private String name;
        private String description;
        private String riskLevel;
        private Map<String, Object> inputSchema;
        private Map<String, Object> outputSchema;
    }

    @Data
    @Builder
    public static class OptionItem {
        private String label;
        private String value;
    }
}

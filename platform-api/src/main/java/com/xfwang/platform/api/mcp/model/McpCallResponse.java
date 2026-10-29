package com.xfwang.platform.api.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP tools/call 执行响应 (符合 Model Context Protocol 规范)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpCallResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private boolean isError = false;

    @Builder.Default
    private List<McpContentItem> content = new ArrayList<>();

    public static McpCallResponse success(String text) {
        return McpCallResponse.builder()
            .isError(false)
            .content(List.of(McpContentItem.text(text)))
            .build();
    }

    public static McpCallResponse error(String errorMessage) {
        return McpCallResponse.builder()
            .isError(true)
            .content(List.of(McpContentItem.text(errorMessage)))
            .build();
    }
}

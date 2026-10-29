package com.xfwang.platform.api.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * MCP 消息内容项 (text, image, resource 等)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpContentItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private String type = "text";

    private String text;

    public static McpContentItem text(String text) {
        return McpContentItem.builder()
            .type("text")
            .text(text)
            .build();
    }
}

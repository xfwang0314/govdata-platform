package com.xfwang.platform.api.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * MCP tools/call 执行请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpCallRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String toolName;

    private Map<String, Object> arguments;
}

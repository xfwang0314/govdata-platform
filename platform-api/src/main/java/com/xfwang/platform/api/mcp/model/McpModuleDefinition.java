package com.xfwang.platform.api.mcp.model;

import java.io.Serial;
import java.io.Serializable;

/** 由业务模块拥有的 MCP 模块声明，平台网关不维护业务模块枚举。 */
public record McpModuleDefinition(String code, String label) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public McpModuleDefinition {
        if (code == null || code.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("MCP 模块编码和名称不能为空");
        }
    }
}

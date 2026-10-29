package com.xfwang.platform.api.mcp.service;

import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.model.McpModuleDefinition;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * 业务模块向 MCP 网关注册受控工具的标准贡献者契约。
 */
public interface McpToolContributor {

    /** 所属业务模块。编码由贡献模块拥有，网关只负责聚合。 */
    McpModuleDefinition module();

    /** 声明该分类下的受控 MCP Tools */
    List<McpToolDefinition> tools();

    /**
     * 执行具体工具调用。
     *
     * @param toolName 工具名称
     * @param arguments 输入参数 (Map结构)
     * @param subject 当前经认证与委托计算的 SubjectContext
     * @return 执行结果
     */
    McpCallResponse execute(String toolName, Map<String, Object> arguments, SubjectContext subject);
}

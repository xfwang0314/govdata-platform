package com.xfwang.platform.api.mcp.model;

import com.xfwang.platform.api.mcp.enums.McpRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * MCP 标准 Tool 元数据定义 (符合 Model Context Protocol tools/list 规范)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具唯一名称（仅使用字母、数字、下划线、连字符和点，例如 datasource.query_dataset） */
    private String name;

    /** 所属模块编码，必须与 Contributor.module().code() 一致。 */
    private String moduleCode;

    /** 中文描述，帮助大模型理解该工具的用途与使用时机 */
    private String description;

    /** 输入参数 JSON Schema (结构化 Map) */
    private Map<String, Object> inputSchema;

    /** 可选输出 JSON Schema。 */
    private Map<String, Object> outputSchema;

    /** 风险等级: LOW(只读探查), MEDIUM(受控查询), HIGH(写操作/任务触发) */
    @Builder.Default
    private McpRiskLevel riskLevel = McpRiskLevel.LOW;
}

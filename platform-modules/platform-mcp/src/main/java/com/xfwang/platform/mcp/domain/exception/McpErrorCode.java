package com.xfwang.platform.mcp.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/**
 * MCP 与 Agent 凭据模块稳定错误编码。
 *
 * <p>错误码面向 MCP 客户端、管理前端和审计记录，禁止由调用方解析中文消息。</p>
 */
@Getter
@AllArgsConstructor
public enum McpErrorCode implements ErrorCode {

    TOKEN_MISSING("GOV-MCP-0401-01", "缺少 Agent 访问凭据", 401),
    TOKEN_INVALID("GOV-MCP-0401-02", "Agent 访问凭据无效", 401),
    TOKEN_EXPIRED("GOV-MCP-0401-03", "Agent 访问凭据已过期", 401),
    TOKEN_INACTIVE("GOV-MCP-0403-01", "Agent 访问凭据未启用", 403),
    TOKEN_TENANT_INVALID("GOV-MCP-0403-02", "Agent 访问凭据未绑定有效租户", 403),
    TOKEN_NOT_FOUND("GOV-MCP-0404", "Agent 访问凭据不存在", 404),
    TOKEN_ACCESS_DENIED("GOV-MCP-0403-03", "无权操作该 Agent 凭据", 403),
    TOKEN_REVOKED("GOV-MCP-0409-01", "已吊销的 Agent 凭据不可修改", 409),
    TOKEN_STATUS_TRANSITION_INVALID("GOV-MCP-0409-02", "Agent 凭据状态不允许当前操作", 409),
    TOKEN_CONCURRENT_MODIFICATION("GOV-MCP-0409-03", "Agent 凭据已被其他请求修改，请刷新后重试", 409),
    TOKEN_EXPIRE_TIME_INVALID("GOV-MCP-0400-01", "凭据过期时间必须晚于当前时间", 400),
    MODULE_CODE_INVALID("GOV-MCP-0400-02", "包含未注册的 MCP 模块编码", 400),
    TOOL_NOT_FOUND("GOV-MCP-0404-01", "MCP 工具不存在", 404),
    TOOL_MODULE_FORBIDDEN("GOV-MCP-0403-04", "Agent 凭据无权调用该工具模块", 403),
    HIGH_RISK_CONFIRMATION_REQUIRED("GOV-MCP-0409-04", "调用高风险 MCP 工具必须显式确认", 409),
    RATE_LIMIT_EXCEEDED("GOV-MCP-0429", "Agent 调用配额已耗尽", 429);

    private final String code;
    private final String message;
    private final int httpStatus;
}

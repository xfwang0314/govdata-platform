# Agent Delegation 与 MCP 架构

- 状态：架构基线 v1（前瞻设计）
- 适用范围：Agent 身份、委托、任务和领域 MCP Tool
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 身份与委托

Agent 是独立 AGENT Principal，不冒充用户、不持有用户 Token。Delegation 记录 delegator、agent、
task、purpose、resource scope、actions、effective/expire time 和状态。

有效权限为：

```text
delegator permissions
∩ agent base capabilities
∩ delegation scope
∩ ABAC conditions
```

用户权限、Agent 能力或 Delegation 任一撤销后，运行时 Grant 和缓存立即失效。后台自主 Agent 使用
自己的固定最小 Grant，不伪造用户委托。

## MCP 结构

模块化单体只部署一个 `platform-mcp` 协议入口；领域模块提供 Tool Provider。未来领域服务拆分时，
Provider 可以变成独立 MCP Server。

```text
Agent → platform-mcp → Tool Provider → Application Interface
                    ↘ platform-auth / audit
```

MCP Tool、Ontology SemanticAction 和 Authorization Action 分开建模，通过 ToolActionBinding 关联。
Tool 输入输出使用 JSON Schema，并映射到本体 Concept。

## 安全

- 每次 Tool 调用重新执行身份、Delegation、RBAC、ABAC 和 Grant 校验。
- Agent 不直接连接数据库，只能调用注册 Tool。
- 高风险 Tool 支持人工确认、用途限制、限时和限量。
- MCP 入口令牌不得透传给下游；下游使用独立受控凭据。
- 审计同时记录 actor、delegator、agentTaskId、tool、resource、decision 和 traceId。


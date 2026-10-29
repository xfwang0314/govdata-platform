# Agent Delegation 与 MCP 架构

- 状态：架构基线 v1（前瞻设计）
- 适用范围：Agent 身份、委托、任务和领域 MCP Tool
- 所有者：GovData Platform Team
- 最后更新：2026-08-21

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

在尚未创建显式 Delegation 的 `OWNER_INHERIT` 模式下，Delegation 项不参与计算，Agent 动态使用归属用户的
当前角色、部门路径和授权属性。启用显式 Delegation 后，它只作为额外权限上限参与交集，不能与归属用户权限做并集。

用户权限、Agent 能力或 Delegation 任一撤销后，运行时 Grant 和缓存立即失效。后台自主 Agent 使用
自己的固定最小 Grant，不伪造用户委托。

## MCP 结构

模块化单体只部署一个 `platform-mcp` 协议入口；领域模块提供 Tool Provider。未来领域服务拆分时，
Provider 可以变成独立 MCP Server。

协议入口采用与 Spring Boot 3.5 对齐的 Spring AI 1.1.x WebMVC Starter，主传输为
Streamable HTTP：

```text
POST/GET/DELETE /platform/mcp
```

不提供旧 HTTP+SSE 双端点；所有客户端统一使用 `/platform/mcp`。协议解析由官方 MCP Java SDK 完成，
`platform-mcp` 只负责认证、租户、授权、配额和领域工具调度。

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
- Agent Bearer Token 使用高熵随机值，数据库只保存 SHA-256 摘要与脱敏前缀；明文只在创建响应中
  返回一次，列表、详情、日志和数据库都不得恢复或展示。
- MCP 协议路径从 Sa-Token 登录拦截器中精确排除，但必须通过 MCP Transport Security Validator
  校验 Agent Bearer Token；管理页面接口继续使用 Sa-Token 与权限码。

## 当前实现边界

当前 `authz_agent_token` 保存 Agent 稳定编码、归属用户和允许模块，用于完成最小可用的 Agent 认证与权限收敛调用链；
正式的 Agent Identity、Task 与可撤销 Delegation 聚合仍属于下一阶段。新增写入型或跨部门 Tool 前，
必须先补齐该聚合，不得把“允许模块”误当成完整 Delegation。现有 LOW/MEDIUM 只读工具仍会在调用时
执行租户隔离和 ABAC/Grant；HIGH 工具必须显式确认，且上线前还需绑定具体资源与 Action。

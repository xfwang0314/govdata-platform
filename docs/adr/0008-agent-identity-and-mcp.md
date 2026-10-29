---
status: accepted
scope: Agent 与 MCP
owner: GovData Platform Team
last_updated: 2026-08-21
---
# Agent 独立身份、有限委托与统一 MCP 入口

Agent 不冒充用户，而以独立 Principal 接受限时、限资源、限动作 Delegation；实际权限取委托人、Agent 能力、委托范围和 ABAC 的交集。模块化单体阶段使用统一 `platform-mcp` 入口，各领域贡献 Tool Provider，并通过本体 SemanticAction 建立语义映射。

统一入口只采用 Streamable HTTP，基于 Spring AI WebMVC Starter 与官方 MCP Java SDK，不提供旧版
HTTP+SSE 双端点。Agent Token 只持久化不可逆摘要，明文仅创建时返回一次。Spring Boot 3.5
使用 Spring AI 1.1.x 兼容线，不为引入 MCP 单独升级到 Spring Boot 4 / Spring AI 2.x。

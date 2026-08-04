---
status: accepted
scope: Agent 与 MCP
owner: GovData Platform Team
last_updated: 2026-08-01
---
# Agent 独立身份、有限委托与统一 MCP 入口

Agent 不冒充用户，而以独立 Principal 接受限时、限资源、限动作 Delegation；实际权限取委托人、Agent 能力、委托范围和 ABAC 的交集。模块化单体阶段使用统一 `platform-mcp` 入口，各领域贡献 Tool Provider，并通过本体 SemanticAction 建立语义映射。

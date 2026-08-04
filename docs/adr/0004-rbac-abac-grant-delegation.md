---
status: accepted
scope: 平台授权
owner: GovData Platform Team
last_updated: 2026-08-01
---
# RBAC、ABAC、Grant 与 Delegation 分层

RBAC 负责功能准入，ABAC 负责具体 Resource 和动态行列约束，AuthorizationGrant 表达明确授权，Delegation 表达用户或服务向 Agent 的有限委托。平台使用自研受限策略能力而非通用脚本/重型策略框架，换取更小依赖和更贴合数据治理的 Obligation，同时承担严格测试和安全维护责任。

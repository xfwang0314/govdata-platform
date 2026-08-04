# RBAC、ABAC、Grant 与 Delegation

- 状态：架构基线 v1（已确认）
- 适用范围：所有功能和数据授权
- 所有者：GovData Platform Team
- 最后更新：2026-08-02

## 职责

```text
Tenant                地区硬隔离
RBAC                  功能、菜单和操作类型准入
ABAC                  具体 Resource、环境与行列约束
AuthorizationGrant    明确、限时、可撤销的资源授权
Delegation            将已有权限有限委托给 Agent
```

决策基线：

```text
tenant matches
AND RBAC permits function
AND (Policy permits OR active AuthorizationGrant exists)
AND Delegation valid when actor is AGENT
AND no explicit DENY
```

显式 DENY 优先；异常、未知属性、无匹配允许项时默认拒绝。

## ABAC 输入与输出

输入由 `Subject + Resource + Action + Environment` 组成。平台维护统一属性 Schema，
领域属性使用命名空间扩展。策略使用受限 JSON DSL，不接受 SQL 或脚本。

决策输出不只包含 Allow/Deny，还包含结构化 Obligation：行过滤、字段白名单、脱敏、
最大行数、导出限制等。执行点必须消费 Obligation，不能只检查布尔结果。

## Policy 生命周期

```text
DRAFT → REVIEWING → PUBLISHED → DISABLED → ARCHIVED
```

已发布版本不可原地修改；发布前执行语法、属性和策略模拟；高风险策略需要审批；审计引用
Policy ID 和版本；策略发布或停用必须清理相关缓存。

## Grant 与 Delegation

共享、管理员授权、API 订阅和 Agent 委托等业务事实可以投影为运行时 AuthorizationGrant。
Grant 保留 `sourceType/sourceId`，但不复制业务协议。Delegation 的有效权限是用户权限、
Agent 基础能力、本次委托范围和 ABAC 条件的交集。

## 旧 data_scope

旧 `data_scope` 继续保护 RuoYi 用户、部门、角色等系统管理列表。新 `govdata-*` 模块不得
使用旧数据权限注解；它们通过 `platform-auth` 和 `govdata-query` 执行资源授权。

当前实现细节和未完成项见 [platform-auth.md](platform-auth.md)。

## 第一版范围

第一版只交付 Human 主体的 `Resource + ABAC + AuthorizationGrant`，但必须同时交付 Policy/Grant
管理服务与页面，以及执行查询类 Obligation 的 `govdata-query`。SERVICE、AGENT Delegation、
BreakGlass 和属性 Schema 注册按后续阶段启用，不得因此削弱第一版的版本化策略、授权来源追踪、
默认拒绝和完整审计。

在 SERVICE、AGENT 的可信认证和 Delegation 决策链启用前，Policy/Grant 管理接口必须拒绝创建
这两类主体的可执行授权，不能仅因表结构已经预留主体类型就提前放行。

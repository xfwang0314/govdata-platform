# 授权与安全开发规范

- 状态：v1（强制）
- 适用范围：认证、授权、数据访问、Agent 和 MCP
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

- tenantId 来自可信认证上下文；跨租户 Grant 被禁止。
- RBAC 校验功能入口，ABAC 校验具体 Resource；任一缺失不得默认放行。
- DENY 优先，错误和未知属性默认拒绝。
- 条件任一引用属性缺失或值不可比较时整个条件不匹配；`NE`、`NOT_IN` 也不得因缺失属性放行。
- Policy 使用受限 JSON DSL和注册属性，禁止 SQL、SpEL、Groovy、JavaScript。
- Obligation 必须在 govdata-query、服务、导出或对应执行点真正执行。
- 字段白名单 `null` 表示未施加字段约束，空集合表示禁止全部字段；多个白名单取交集后不得把空集解释为无限制。
- 多个脱敏 Obligation 冲突时只允许保持或增强保护，`NONE` 不得覆盖已有脱敏规则。
- govdata 模块禁止旧 DataPermission/data_scope；RuoYi 系统管理暂时保留。
- 平台管理员默认无租户业务数据权限；BreakGlass 必须审批、限时和审计。
- SecretValue、Token、私钥不进入日志、响应、事件或 Agent 上下文。
- Agent 不冒充用户；Delegation 不得超过用户权限、Agent 能力和任务范围。
- MCP 每次 tools/call 重新校验，不把入口令牌透传给下游。
- 高风险操作（导出、共享、策略发布、应急访问）需要明确用途和必要审批。
- 缓存键包含租户、主体、资源、动作和策略版本；撤销/发布立即失效。

# 测试与质量门禁

- 状态：v1（强制）
- 适用范围：所有代码和数据库改动
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 分层

- Unit：领域规则、策略、规则编译和值对象。
- Module Integration：MySQL、Redis、MyBatis 和事务。
- Contract：govdata-api、Connector、SeaTunnel/Secret/Event Adapter。
- Architecture：Maven Enforcer 与 ArchUnit 禁止方向。
- Security Integration：租户、ABAC、Grant、Delegation、行列权限和 Secret。
- E2E：共享、授权、查询、交换和质量关键闭环。

## 必测不变量

跨租户拒绝、默认拒绝、DENY 优先、Grant 到期、缓存失效、行列权限不可绕过、Agent 委托取交集、
MCP 不旁路、Secret 不泄漏、事件重复消费幂等、baseline 空库执行成功。

Connector Adapter 使用统一合同测试验证 capability、配置、超时、资源释放、错误映射、并发和凭据保护。
测试通过外部 Interface 验证行为，不锁定实现细节。CI 显式启用全部测试；不得通过 skip、删除断言或
放宽安全条件规避失败。


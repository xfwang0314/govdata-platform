# 测试与质量门禁

- 状态：v1（强制）
- 适用范围：所有代码和数据库改动
- 所有者：GovData Platform Team
- 最后更新：2026-08-04

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

## 实现模块最低要求

- Domain：Entity 状态机和 DomainService 使用不启动 Spring 的纯单元测试。
- Application：验证用例编排、权限失败、乐观锁、外部错误映射和敏感字段保护。
- Mapper：自定义 SQL、JSON、租户、乐观锁、逻辑删除、复杂分页和 MySQL 特性必须使用 MySQL 8 集成测试。
- Controller：主要接口验证 URL、HTTP 方法、Validation、RBAC、`R<T>`、ErrorCode、traceId、时间和枚举序列化。
- Architecture：Controller 不依赖 Mapper/Entity、Domain 白名单、API 契约纯度、模块方向和旧根级包必须自动检查。
- SQL：新脚本必须通过静态门禁；Baseline、Upgrade、Mapper 方言、CHECK、JSON、租户和
  乐观锁必须使用真实 MySQL 8 验证，禁止 H2 代替。

普通功能至少包含“存在规则时的 Domain 测试 + Application 用例测试 + 存在自定义 SQL 时的 Mapper 集成测试
+ 一条主要接口集成测试”。简单继承框架 Mapper 的方法不重复测试框架实现。

## MySQL Baseline 隔离验证

`MySqlBaselineIntegrationTest` 默认禁用。只有确认 `application-dev.yml` 指向可测试的 MySQL 8、
且当前账号允许创建临时数据库时，才设置环境变量 `GOVDATA_MYSQL_BASELINE_TEST=true` 后单独运行。
测试库名固定使用 `govdata_baseline_verify_` 前缀，按 manifest 执行后必须在 `finally` 中删除；
不得把测试切换到现有业务库，也不得移除显式环境变量门禁。

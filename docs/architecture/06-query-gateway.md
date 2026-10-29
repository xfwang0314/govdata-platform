# 统一数据查询网关

- 状态：MySQL 单表受控查询、访问审计和数据行 MCP 已实现
- 适用范围：外部数据源在线只读查询
- 所有者：GovData Platform Team
- 最后更新：2026-09-07

## 定位

`govdata-query` 是元数据预览、数据质量、数据服务、共享在线查询和 Agent Tool 访问外部数据的
唯一在线查询入口。它不负责平台 MySQL 业务表，也不替代 SeaTunnel 批量交换。

`govdata-query` 是 `Resource + ABAC + AuthorizationGrant` 第一版闭环的强制执行点，不是可选的
后续优化。任何外部数据查询在获得 `PERMIT` 后，必须由本模块消费并执行行过滤、字段投影、
脱敏、最大行数和导出限制等 Obligation；只检查 Allow/Deny 不视为完成授权接入。

## Interface

调用者提交结构化 AuthorizedQueryPlan，包含 dataSourceId、resourceId、字段、过滤、聚合、
排序、分页、最大行数、超时和用途。接口不得接受原始 SQL。

## 执行链

```text
逻辑请求
→ 元数据字段解析
→ ABAC/Grant 决策
→ 合并行级 Obligation
→ 字段投影与脱敏
→ 方言 AST 编译
→ 只读连接执行
→ 限量和审计
```

## 安全不变量

- 第一阶段只支持 SELECT；数据源账号只读。
- 字段和关系必须来自已注册元数据，值使用参数绑定。
- 强制超时、并发、最大扫描/返回行数和结果大小。
- 查询结果离开 Gateway 前完成字段隐藏和脱敏。
- 查询审计记录资源、字段集合、行数、用途、耗时和 traceId，不记录敏感原值。
- 导出使用独立异步能力和单独授权，不能通过放大分页模拟导出。

## Adapter

方言、连接和外部系统差异位于内部 Adapter。测试通过统一查询合同验证 MySQL、Oracle、Doris、
ClickHouse 等实现，调用者不感知具体驱动。

## 当前实现

已实现：

- `AuthorizedQueryGateway` 单一调用 Interface。
- 只接受数据源、Resource、字段 ID、结构化过滤、排序、分页、超时和用途，不接受原始 SQL。
- 强制读取 `AuthorizationContextAccessor`，请求 Resource 与已授权 Resource 不一致时拒绝。
- 通过 `QueryResourceSchemaProvider` 获取受信任物理对象和字段映射。
- 字段白名单、行级 RowFilter、最大行数和超时上限编译。
- `subject.*` 行过滤值从授权主体上下文解析，未知属性默认拒绝。
- Connector Adapter 选择、返回结果二次字段裁剪、限量和常用脱敏规则。
- 查询异常编码与授权约束合同测试。

`govdata-metadata` 提供生产 `QueryResourceSchemaProvider`；`govdata-datasource` 提供 MySQL
`QueryExecutionAdapter`。公开网关先写开始审计，通过独立的 `@ResourceAuthorize` 入口建立表级
`QUERY / DATA_QUERY` 决策，再由执行核心消费行列、脱敏和限量约束，结束审计成功后才交付结果。
无授权上下文的执行核心/Adapter 调用仍默认拒绝；不能借用 VIEW 决策读取数据。

MySQL 第一版要求 ACTIVE、READ_ONLY、连接测试成功，且凭据用途允许 `DATASOURCE_QUERY`。
每次连接检查实际 MySQL GRANT，仅支持不带角色、GRANT OPTION 和其他权限的 SELECT/USAGE 账号。
连接的自定义驱动参数不透传到在线查询；禁用多语句、LOCAL INFILE 和对象反序列化。
注册标识符引用、过滤值绑定、稳定主键排序、JDBC/服务端超时和返回限额均由 Adapter 执行。
第一版只查询数据源配置的 database；无主键、跨库、二进制/其他不支持类型默认拒绝，不降级为原始 SQL。

`gd_query_access_log` 以同一服务端查询 ID 关联 STARTED 与 SUCCEEDED/DENIED/FAILED 追加事件；
与授权及凭据审计共用 traceId。开始或结束审计失败都不交付结果，不保存 SQL、参数值和结果数据。
表结构、索引及保留策略见 [查询审计评审](../database/reviews/03-query-access-review.md)。

存在 Schema Provider 与 Adapter 时，MCP 自动注册 `datasource.query_dataset` 和
`datasource.sample_dataset_records`，分别最多 50/5 行；默认用途为已登记的 `DATA_QUERY`。
字段 ID 使用字符串，不接受 Raw SQL；工具沿用 Token 模块范围和主体上下文，不自动授予资源权限。

待实现：

- Oracle、Doris、ClickHouse Adapter 合同测试。
- 独立异步导出流程。

在线查询是同步、短时、强制超时的请求，不使用 Quartz。需要周期执行的元数据采集、质量计划和
交换计划属于各自领域调度，不得把调度框架依赖放入查询核心。

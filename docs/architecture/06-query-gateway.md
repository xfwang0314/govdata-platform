# 统一数据查询网关

- 状态：第一阶段核心已实现，等待元数据与 Connector Adapter 接入
- 适用范围：外部数据源在线只读查询
- 所有者：GovData Platform Team
- 最后更新：2026-08-02

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

待实现：

- `govdata-metadata` 的 QueryResourceSchemaProvider。
- `govdata-datasource` 的 MySQL 第一版 QueryExecutionAdapter、只读连接和 SecretRef。
- `gd_query_access_log` 追加审计。
- Oracle、Doris、ClickHouse Adapter 合同测试。
- 独立异步导出流程。

在线查询是同步、短时、强制超时的请求，不使用 Quartz。需要周期执行的元数据采集、质量计划和
交换计划属于各自领域调度，不得把调度框架依赖放入查询核心。

# 受控查询

- 状态：MySQL 查询、真实数据库授权链路、访问审计与数据行 MCP 已实现
- 适用范围：`govdata-query`
- 所有者：GovData Platform Team
- 最后更新：2026-09-07

受控查询上下文是所有外部数据在线读取的唯一执行入口，负责把授权决策转换为不可绕过的查询约束，
不拥有数据源配置或元数据事实。

## Language

**Authorized Query**:
只包含稳定数据源、Resource、字段 ID、结构化过滤、排序、限量、超时和用途的查询意图。
_Avoid_: 原始 SQL、列名拼接

**Query Resource Schema**:
由元数据模块提供的受信任查询对象和字段映射快照。
_Avoid_: 调用方传入的表名、字段名

**Authorized Execution Plan**:
查询网关完成 Resource 授权、字段校验和 Obligation 合并后交给 Connector Adapter 的只读执行计划。
_Avoid_: 用户查询请求透传

**Query Execution Adapter**:
针对一种 Connector 参数化编译并执行只读计划的 Adapter。
_Avoid_: `switch datasourceType`、万能 JDBC 工具

**Protected Query Result**:
在查询网关内完成字段裁剪、限量和脱敏后才允许返回调用方的结果。
_Avoid_: 原始 ResultSet、未脱敏 Map

## 当前实现边界

- 已有 `AuthorizedQueryGateway`、授权上下文校验、字段投影/脱敏/限量的执行 seam。
- `govdata-metadata` 已提供生产 `QueryResourceSchemaProvider`。
- 数据源模块提供 MySQL `QueryExecutionAdapter`，通过 SecretRef 解析实际只读账号。
- 公开 Gateway 先审计、再建立新鲜 QUERY 授权，随后消费行列约束并在结束审计成功后返回。
- MCP 自动注册数据行查询/抽样 Tool；不自动授予 Agent 对数据的访问权。
- 查询模块拥有 `gd_query_access_log`，仅保存开始/结束事件及非敏感指标。

## Invariants

- 没有当前 `PERMIT` AuthorizationContext 时拒绝执行。
- 查询 Resource 必须与授权 Resource 完全一致。
- 请求只使用元数据字段 ID，不接受 SQL、表名或列名。
- Adapter 只接受 AuthorizedExecutionPlan，必须参数绑定。
- Obligation 中未知字段、属性或操作符默认拒绝。
- 结果离开模块前必须再次裁剪、限量和脱敏。
- 在线查询不使用 Quartz；异步导出和周期任务属于独立调度流程。

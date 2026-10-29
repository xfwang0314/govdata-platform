# 数据接入、查询与元数据表设计

- 状态：实施基线 v4
- 范围：`govdata-datasource`、`govdata-query`、`govdata-metadata`、`govdata-metadata-harvest`
- 数据库：MySQL 8.0
- 最后更新：2026-08-23

## 领域边界

- `govdata-datasource` 拥有连接实例、Connector 配置和连接测试，密码与 Token 只保存 `secretRef`。
- `govdata-query` 消费稳定表/字段资产 ID 和当前 Schema，不保存任意 SQL。
- `govdata-metadata` 拥有当前技术目录、表级自动修订、完整字段修订快照、变更问题和可重建影响投影。
- `govdata-metadata-harvest` 拥有单数据源采集计划、不可变配置修订、运行和原始结果引用。
- 模块之间只通过 `govdata-api` 契约协作，不访问其他模块的表、Mapper 或 Entity。

## 数据源与查询

| 模块 | 表 | 用途 |
|---|---|---|
| datasource | `gd_datasource` | 数据源稳定身份、非敏感端点配置、Connector、权属和状态 |
| datasource | `gd_datasource_connection_test` | 追加保存脱敏连接测试结果、耗时、主体和 traceId |
| query | `gd_query_access_log` | 追加保存查询开始/结束、主体、资源、用途、数量、耗时和错误码；traceId 关联授权/Secret 审计 |
| query | `gd_query_export_job`（规划） | 异步导出计划与状态 |
| query | `gd_query_export_artifact`（规划） | 受控导出文件引用 |

Redis、SFTP 等非 JDBC Connector 不伪装成关系数据库。只有实现元数据发现能力的 Connector 才能被采集计划使用。

## 元数据九张表

| 表 | 类型 | 解决的问题 |
|---|---|---|
| `gd_metadata_asset` | 业务实体 | 保存 Catalog、Schema、Table、Column 的稳定 ID、真实层级、源端定位、当前状态和治理属性 |
| `gd_metadata_catalog` | 明细 | 保存源端真实存在的 Catalog 技术属性 |
| `gd_metadata_schema` | 明细 | 保存源端真实存在的 Schema 技术属性 |
| `gd_metadata_table` | 明细 | 保存表类型、注释、存储和容量等当前技术属性 |
| `gd_metadata_column` | 明细 | 保存字段当前结构及字段自身修订号 |
| `gd_metadata_table_revision` | 追加历史 | 表技术内容变化时追加表内独立修订、差异摘要和追溯信息 |
| `gd_metadata_table_revision_column` | 追加历史 | 保存某次表修订下完整、不可变的字段结构 |
| `gd_metadata_change_issue` | 业务实体 | 对删除字段、收紧可空、类型/主键变化等破坏性变化建立去重处置事项 |
| `gd_metadata_impact_projection` | 可重建投影 | 汇总交换、质量、标准和数据服务等来源模块声明的表/字段依赖，不拥有消费者事实 |

### 身份、层级与权限

数据源业务实体及其 Resource 已由 `govdata-datasource` 创建，元数据模块不得再创建“元数据数据源”。实际层级由 Connector 决定：

- MySQL：`DataSource → Catalog → Table → Column`
- Oracle：`DataSource → Schema → Table → Column`
- PostgreSQL、SQL Server：`DataSource → Catalog → Schema → Table → Column`

禁止创建 `default_catalog`、`default_schema` 等占位节点。Catalog、Schema、Table 投影 Resource，并挂到已有 DataSource Resource 下；Column 是稳定元数据资产，但不创建 Resource，授权继承所属表并由字段 Obligation 约束。

页面中的“元数据目录”只是上述资产的树形读模型，不是第二套目录表。未来面向业务主题的数据集/资源目录由独立 Dataset 或目录领域建设，不与技术命名空间混用。

### 当前状态与修订

- 成功观察到的技术状态按有界批次立即生效，不存在整库待发布快照。
- 每张表独立递增 `current_revision_no`；每次修订保存当时完整字段结构。
- 注释变化为 `DOCUMENTATION_ONLY`，兼容新增为 `NON_BREAKING`，删除字段等高风险变化为 `BREAKING`。
- `BREAKING` 生成 ChangeIssue，但不阻断当前元数据和下游服务。
- 下游绑定稳定 `asset_id`；可记录验证过的修订号用于运行前提示，但不永久钉死物理版本。
- 可靠 `native_object_id` 优先维持身份；对象短暂缺失后恢复时复用原资产 ID，而不是再次新增。

## 元数据采集六张表

| 表 | 类型 | 解决的问题 |
|---|---|---|
| `gd_metadata_harvest_plan` | 业务实体 | 一个计划唯一绑定一个数据源，保存当前调度、范围和权属 |
| `gd_metadata_harvest_plan_revision` | 配置版本 | 每次计划修改追加不可变配置修订，运行固定引用该修订 |
| `gd_metadata_harvest_rule` | 版本明细 | 保存某计划修订的受控包含/排除规则 |
| `gd_metadata_harvest_run` | 运行 | 保存单数据源运行、Connector、计数、状态、主体和 traceId |
| `gd_metadata_harvest_run_failure` | 追加记录 | 保存最终隔离到命名空间批次或单表的脱敏失败 |
| `gd_metadata_harvest_raw_result` | 追加记录 | 可选保存对象存储引用、哈希、大小和保留期，不把原始响应写入 MySQL |

同一租户内一个数据源最多存在一个采集计划，同一数据源同时只允许一个运行。Connector 分批返回命名空间和“完整表+完整字段”；采集模块执行规则后调用目录批量写入契约。批次失败会缩小到单表隔离，已成功表不回滚；失败可整次重跑，不提供通用断点续采。Cron 默认通过可替换调度 seam 的 Quartz Adapter 触发。

只有无 Catalog/Schema 限定、无过滤规则且发现完整成功的运行才具有“全数据源权威范围”，此时才允许把未观察到的旧资产标记为 `MISSING`。候选消失数量和比例超过后端安全阈值时运行进入 `ANOMALOUS` 并暂停缺失生效。限定范围内外的既有资产保持可服务，不因计划范围改变而被误判或阻断。

规则仅支持 `EXACT`、`PREFIX`、`SUFFIX`、`CONTAINS`，禁止正则、SQL、SpEL、Groovy 和 JavaScript。

## 批量写入与容量

写入采用三阶段：Catalog 批次、Schema 批次、完整 Table/Column 有界批次。每批先批量读取已有稳定资产，在内存完成身份匹配、哈希和差异计算，再批量写当前资产、类型明细、修订字段和 ChangeIssue；禁止逐字段查询和逐字段提交。单张表及其字段必须位于同一短事务，外部 JDBC 发现不得位于该事务内。

修订、失败和原始归档属于高增长数据：按所属表/运行与时间建立索引，原始归档按保留期分批清理；长期分析进入 Doris 或 ClickHouse，不在控制库执行无界聚合。

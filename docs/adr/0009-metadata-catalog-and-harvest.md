---
status: accepted
scope: 元数据目录、采集与受控查询
owner: GovData Platform Team
last_updated: 2026-08-26
---
# 当前元数据、自动修订与采集执行分离

## 决策

`govdata-metadata` 保存可信的当前技术元数据，并在单表技术内容发生变化时自动追加表修订和完整字段快照。
采集结果无需整库发布或人工审核即可按成功批次生效。高风险变化创建可处置的 ChangeIssue，但不得阻塞当前
元数据、查询或下游服务。

`govdata-metadata-harvest` 管理单数据源采集计划、配置修订、运行、失败明细和可选原始归档。它通过
`govdata-api` 调用数据源发现能力和目录写入能力，不访问两个相邻模块的表或实现类。同一数据源最多绑定一个
有效计划，同一数据源的运行串行。

采集计划仍是领域事实；动态触发通过 `HarvestScheduleRegistrar` 端口注册。第一版默认 Adapter 为嵌入式
Quartz，后续接入其他调度产品时只新增 Adapter，不把 Quartz Job 或触发器模型泄漏到采集领域。

DataSource 继续由数据源模块拥有并只投影一次 Resource。目录模块仅投影 Catalog、Schema、Table：

```text
DataSource Resource → Catalog? → Schema? → Table
                                      └── Column（稳定资产，不投影 Resource）
```

Connector 决定真实命名空间：MySQL 使用 Catalog，Oracle 使用 Schema，PostgreSQL/SQL Server 同时使用
Catalog 和 Schema；禁止创建默认占位节点。元数据页面中的“目录树”是组合查询读模型，不是第二套业务目录或
重复 Resource。未来业务 Dataset/共享目录另建独立聚合。

## 版本语义

- 表和字段使用稳定 Asset ID；重命名或恢复优先依赖可靠原生 ID、身份历史和规范化键确认连续性。
- 每张表独立递增 `revisionNo`；每次表修订保存完整字段快照。
- `structureHash` 只覆盖影响执行的结构，`contentHash` 额外覆盖源端技术注释；治理字段不触发技术修订。
- 下游绑定稳定表/字段 ID，不绑定历史修订号；运行前可以记录已验证修订号用于审计和变化提示。
- `MISSING` 只允许在一次完整成功运行后判定；缺失比例或数量越过保护阈值时，本次运行标记异常并禁止批量
  置为 `MISSING`，避免权限、网络或 Connector 异常污染目录。
- 表缺失与破坏性字段变化都形成结构化 ChangeIssue；`OUT_OF_SCOPE`、`MISSING` 均不由元数据模块统一阻断下游。
- 人工治理属性可独立维护，不改变源端技术事实和技术修订号。
- 交换、质量、共享和服务等消费者拥有真实依赖关系，通过 `MetadataImpactRegistry` 向元数据模块写入可重建
  影响投影；目录模块不反向访问消费者业务表。

## 批处理与一致性

按 Catalog 批次、Schema 批次、完整 Table+Column 有界批次写入。批次开始时一次读取已有资产和字段，在内存中
比较，批量插入/更新当前表、修订、字段快照与 ChangeIssue。当前资源投影通过事务后事件执行，可靠跨模块
事件待接入平台统一 Outbox。事务以有界批次提交，但单张表和其全部
字段必须原子生效；批次失败后缩小重试，最终隔离为单表失败。没有数据源级发布事务，也不实现断点续采。

## 后果

删除整库 Snapshot、审核发布、current/effective 双指针和下游迁移编排。保留可追溯修订、结构化差异、稳定
身份与事件，因此标准、质量、API、共享和自动运维可以共享元数据事实，而无需复制版本状态机。

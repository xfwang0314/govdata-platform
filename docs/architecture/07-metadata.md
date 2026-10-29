# 元数据架构

- 状态：架构基线 v4
- 所有者：GovData Platform Team
- 最后更新：2026-08-23

## 模块边界

- `govdata-datasource`：拥有 DataSource、Connector、SecretRef 和外部发现能力。
- `govdata-metadata-harvest`：拥有单数据源计划、配置修订、规则、运行、失败和原始归档。
- `govdata-metadata`：拥有可信当前技术元数据、稳定资产身份、自动表修订、差异、变更问题和可重建影响投影。
- `govdata-query`：通过 `govdata-api` 读取当前受信任 Schema，不读取目录表或固定历史修订。

## 资源与目录树

DataSource 已由数据源模块投影为 Resource，元数据模块不得再建 DataSource Asset 或 Resource。Connector 的实际
命名空间决定其子链：

```text
MySQL:                  DataSource Resource → Catalog → Table → Column
Oracle:                 DataSource Resource → Schema → Table → Column
PostgreSQL/SQL Server:  DataSource Resource → Catalog → Schema → Table → Column
```

Catalog、Schema、Table 投影 Resource；Column 是稳定元数据资产但继承表权限。
页面采用三栏 Explore：数据库类型、连接及实际命名空间下的表/列分类定位范围，中间分页结果切换卡片和列表，
右侧快速预览并进入完整详情、版本或治理维护。导航分类不创建额外业务资产或Resource。
页面“元数据目录”把数据源展示契约和目录当前状态组合为读模型，不产生重复业务数据。
未来 Dataset/政务共享目录是独立业务聚合。

## 采集写入

Connector 以原始名称、`canonicalKey`、可选可靠 `nativeObjectId` 返回扁平命名空间和完整表批次。采集模块按规则
过滤并转交目录模块：

```text
创建 Run并占用 datasource互斥键
→ Catalog批次
→ Schema批次
→ 完整Table+Column有界批次
→ 每批当前状态与自动修订短事务
→ 完整成功时执行缺失检测
→ 完成Run并释放互斥键
```

Cron 计划通过 `HarvestScheduleRegistrar` seam 注册，默认 Adapter 为 Quartz。业务计划和运行表不保存 Quartz
类型；切换其他调度平台时只替换 Adapter。Quartz 默认采用 `DO_NOTHING` Misfire，启动后以控制库有效计划对账。

批次写入必须先批量读取已存在对象，在内存比较，再批量写当前状态、修订、完整字段快照和 ChangeIssue。
当前资源投影通过事务后事件执行，可靠跨模块事件待统一 Outbox 落地。单表及其字段原子生效；批次失败缩小
重试，最终隔离单表失败。不存在数据源级发布事务或断点续采。

## 当前状态、修订与变化

- 首次观察创建稳定 Asset 和表 `revisionNo=1`。
- 表技术内容哈希未变时只更新最近观察信息，不创建修订。
- `structureHash` 变化产生 `NON_BREAKING/BREAKING` 修订；仅技术注释变化产生 `DOCUMENTATION_ONLY` 修订。
- 每次表修订都保存完整字段快照；字段自身仅在发生变化时递增字段修订号。
- 高风险变化创建去重 ChangeIssue，但当前状态立即生效，不等待审批。
- 完整运行中未观察到的对象标记 `MISSING`；计划范围变化标记 `OUT_OF_SCOPE`。两者均不集中阻断下游。
- 当候选消失表达到后端配置的数量与比例阈值时，运行标记为 `ANOMALOUS` 并暂停 `MISSING` 生效，防止权限
  收缩或探查异常制造大规模误删除。
- 对象重新出现且身份连续时恢复为 `ACTIVE` 并沿用稳定 ID。

下游只保存稳定表/字段 ID。需要严格执行保障的模块可记录“上次验证修订号”，在运行前重新比较并由本领域决定
继续、告警或停止。消费者绑定事实仍由下游模块维护；元数据模块只保存通过 `MetadataImpactRegistry` 提交的
可重建影响投影，用于变化问题页面汇总，不接管消费者生命周期或迁移状态。

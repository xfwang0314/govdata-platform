# OpenMetadata 元数据版本与下游依赖机制调研

> 调研日期：2026-08-23
> 范围：OpenMetadata 官方 1.12.x 文档及官方仓库 `main` 分支公开 Schema/源码。`main` 会继续演进，本文以链接所示内容为依据。

## 结论

OpenMetadata 没有把“每次采集批次、人工审核、表版本发布、所有下游消费者升级”组成一个强耦合状态机。它的核心做法更简单：

1. 每个表只有一个稳定实体身份和一个当前元数据文档；列作为表文档中的嵌套结构存在。
2. 当前文档每次发生有效变化时产生实体历史版本和字段级 `changeDescription`，版本主要服务于审计、比较、事件和回看。
3. 采集直接对当前实体执行创建、更新或跳过；官方模型中没有“观察版本 → 待审核候选 → 治理生效版本”的通用发布链。
4. 质量测试、标签/术语、血缘、数据契约等通常引用当前资产 ID、FQN 或字段路径，不固定某个表历史版本。
5. 需要冻结预期结构并走审批的场景由数据契约等消费者自己建模，而不是让元数据模块为所有消费者维护版本迁移状态。

因此，对政务数据中台最重要的借鉴不是复制 OpenMetadata 的全部表结构，而是采用：

> 元数据模块维护稳定资产身份、当前观察结构、历史和变更事件；各领域绑定资产身份并拥有自己的规则或契约版本。历史元数据版本只用于审计或可重现校验，不作为默认运行时依赖。

## 1. 实体版本是历史记录，不是发布分支

OpenMetadata 为实体维护 `major.minor` 版本：兼容变化增加小版本，不兼容变化增加大版本；删除列是官方给出的重大版本变化示例。历史 API 能列出版本并取回某个版本的完整实体快照。[官方版本说明](https://docs.open-metadata.org/v1.12.x/connectors/ingestion/versioning)；[表 API](https://docs.open-metadata.org/v1.12.x/api-reference/data-assets/tables)

`entityHistory` Schema 定义了完整版本号，以及 `fieldsAdded`、`fieldsUpdated`、`fieldsDeleted`、`previousVersion` 等变更说明。它描述的是“导致当前版本的变化”，没有候选、审批、生效或被取代等发布状态。[官方 `entityHistory.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/type/entityHistory.json)

官方后端存储说明显示，实体历史快照保存在 `entity_extension`，扩展键类似 `table.version.0.1`；当前实体与历史扩展分开存储。[官方后端数据库说明](https://docs.open-metadata.org/v1.12.x/api-reference/main-concepts/backend-db)

这意味着 OpenMetadata 的表版本更接近“同一个聚合的修订历史”，而不是可以同时流转的多个发布候选。

## 2. 表整体版本化，列不是独立顶级实体

官方 Table Schema 把 `columns` 定义为表实体内部的数组；每个列对象包含名称、类型、长度、精度、约束、描述、标签和 FQN，但没有列级实体 UUID 或列级版本字段。表本身才包含 `id`、`version`、`changeDescription` 和 `deleted`。[官方 `table.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/entity/data/table.json)

因此 OpenMetadata 可以在一个表版本中保存完整列集合并生成列路径差异，却不用处理“表 V5 由哪些独立字段版本拼装”的组合问题。代价是列重命名或删除恢复时，不能仅靠官方列模型获得数据库无关的稳定列 UUID。

## 3. 采集更新当前态，不等待整批人工发布

当前官方采集代码会为实体生成 `sourceHash`，由服务端判断创建、更新或跳过未变化实体；采集流程直接将实体送入写入端。代码注释明确把结果归为 create/update/skip，而不是创建待发布候选。[官方 `topology_runner.py`](https://github.com/open-metadata/OpenMetadata/blob/main/ingestion/src/metadata/ingestion/api/topology_runner.py)

数据库采集配置支持数据库、Schema、表过滤和增量采集，并通过 `markDeletedTables` 等开关将源端不再存在的对象软删除。其定义还明确：表软删除可作用于当前正在采集的 Schema，并会处理相关测试套件或血缘。[官方数据库采集 Schema](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/metadataIngestion/databaseServiceMetadataPipeline.json)

这是一种偏目录同步的策略，不是严谨的多次缺失确认机制。政务数据中台可以借鉴 upsert、哈希去重和范围感知，但不应照搬“一次未发现即联动删除”；当前已确认的可信范围、连续缺失和仅通知下游规则更安全。

## 4. 质量测试绑定资产路径，不绑定表版本

TestCase 通过 `entityLink` 指向表或列。官方格式是：

- 表：`<#E::table::tableFQN>`
- 列：`<#E::table::tableFQN::columns::columnName>`

TestCase 自己拥有独立版本和结果历史，但目标引用中没有 `tableVersion`。[官方测试用例 API](https://docs.open-metadata.org/v1.12.x/api-reference/data-quality/test-cases)；[官方 `testCase.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/tests/testCase.json)

据此可以推断：质量规则不是从表 V2 逐次迁移到 V3、V4、V5，而是持续关联当前表/列身份；字段路径失效时由测试执行、删除联动或后续治理暴露问题。

## 5. 标签、术语和类似“标准”的绑定不固定资产版本

表和列都直接携带 `TagLabel`。标签既可来自 Classification，也可来自 Glossary；标签保存 `tagFQN`、来源、应用方式及建议/确认状态，没有目标表版本号。[官方 `tagLabel.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/type/tagLabel.json)

后端存储说明进一步表明，标签与术语应用关系保存在 `tag_usage`，目标通过实体或子字段 FQN 关联。[官方后端数据库说明](https://docs.open-metadata.org/v1.12.x/api-reference/main-concepts/backend-db)

Glossary Term 自己有版本和独立审批工作流；新增术语可以处于 Draft，由 reviewer 批准或拒绝。这说明 OpenMetadata 把“业务术语的治理审批”放在术语聚合自身，而不是阻止技术表 Schema 更新。[官方术语审批工作流](https://docs.open-metadata.org/v1.12.x/how-to-guides/data-governance/glossary/approval)

## 6. 血缘绑定实体身份和字段 FQN，不绑定历史版本

血缘边使用上下游实体 UUID；列级血缘使用源列和目标列的完整限定名，血缘详情还可以关联 Pipeline。Schema 中没有表版本或列版本字段。[官方 `entityLineage.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/type/entityLineage.json)

通用 `EntityReference` 只要求实体 `id` 和 `type`，可以附带名称、FQN、删除状态等，同样没有版本字段。[官方 `entityReference.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/type/entityReference.json)

这使知识图谱关系默认跟随当前实体，而历史版本只在审计时按需读取。

## 7. 数据契约保存自己的期望 Schema

OpenMetadata 的 DataContract 通过 `EntityReference` 绑定当前数据资产，同时在契约自身保存完整 `schema`、质量期望、SLA、状态和独立版本。契约可以是 Draft/Active/Deprecated，并具有自己的审批流程。[官方 `dataContract.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/entity/data/dataContract.json)；[官方数据契约说明](https://docs.open-metadata.org/v1.12.x/how-to-guides/data-contracts/spec)

这是降低耦合的关键：需要稳定预期结构的消费者保存自己的契约快照，元数据表升级不要求该消费者同步改写到新表版本。消费者只需重新验证“当前实际结构是否满足自己的契约”。

## 8. 软删除和恢复保持实体身份

Table API 默认支持软删除，实体返回 `deleted: true`；恢复接口按原表 UUID 恢复。硬删除是显式选项。[官方表删除与恢复 API](https://docs.open-metadata.org/v1.12.x-SNAPSHOT/api-reference/data-assets/tables/delete)

这支持“重新出现优先恢复同一资产身份”的方向，但 OpenMetadata 的公共 API 不能替代数据源原生对象 ID 或人工判断，无法单凭同名证明物理身份连续。

## 9. OpenMetadata 的 API 资产不是数据服务运行时版本适配器

OpenMetadata 的 `APIEndpoint` 是被编目的 API 资产，记录 URL、方法、请求/响应 Schema，并拥有自己的元数据版本。官方 Schema 没有它所依赖的表版本，也没有“从表 V2 切换到 V5”的运行计划字段。[官方 `apiEndpoint.json`](https://github.com/open-metadata/OpenMetadata/blob/main/openmetadata-spec/src/main/resources/json/schema/entity/data/apiEndpoint.json)

所以不能以 OpenMetadata 为依据，把数据服务模块设计成元数据历史版本的部署系统。OpenMetadata 能提供血缘、影响面和变更事件，但 API SQL 能否继续执行、是否重新发布，属于服务平台自身职责。

## 10. 对政务数据中台的设计建议

### 10.1 删除通用“版本迁移矩阵”

不让 API、质量、标准、主数据、交换等模块分别维护：

```text
dependency_version → candidate_version → compatibility → migration → activation
```

这会把每个模块都变成元数据发布系统，并产生组合爆炸。

统一改为：

```text
MetadataAsset
├─ stableAssetId
├─ currentObservedSchema
├─ currentRevision
├─ history
└─ change events

Consumer
├─ stableAssetId / stableColumnId
├─ its own configuration or contract revision
└─ lastValidatedMetadataRevision（仅审计，可空）
```

`lastValidatedMetadataRevision` 表示“上次校验时看到什么”，不是运行时锁定，也不要求逐版本迁移。

### 10.2 表版本只承担三项职责

- 回看某个时间点的完整结构；
- 生成当前修订与上一修订的差异和事件；
- 支持问题审计与消费者按需重放校验。

不再让表版本承担统一审批、生效、授权和下游启停。

### 10.3 各领域只保存自己的稳定约束

- 数据标准绑定稳定资产/字段身份，标准版本独立演进；落标评估读取当前 Schema。
- 数据质量绑定稳定资产/字段身份，规则版本独立演进；每次执行按当前物理结构校验。
- 主数据保存自己的模型、映射和发布版本；元数据只提供发现和变化事件。
- 数据 API 保存查询定义、输出契约和 API 发布版本；不固定元数据历史版本。
- 交换任务保存字段映射和任务版本；运行前由交换模块自行检查当前结构。

若某个领域有强可重现要求，就在该领域的发布版本中保存必要的 Schema 快照或契约，不把这种要求上升为所有模块的通用元数据依赖协议。

### 10.4 API V2 到表 V5 的简化处理

API 不需要执行“绑定表 V2 → 切换表 V5”。建议语义为：

```text
API 发布版本 A2
├─ 绑定 tableAssetId
├─ 保存查询与输出契约
├─ lastValidatedMetadataRevision = V2
└─ 当前仍使用同一个物理数据源
```

表到 V5 后，元数据只发布一次结构变化事件；服务模块可以异步重验 A2：

- 校验成功：仅把 `lastValidatedMetadataRevision` 更新为 V5，不产生新的 API 对外版本；
- 校验失败：记录不兼容告警，由服务模块按自己的策略处理；
- API 对外契约确实需要修改：服务模块创建 A3，与表版本编号无关。

这样不存在“V3、V4 没升级，V5 就切不过去”的问题，也不会让所有领域实现相同的版本切换状态机。

## 最终取舍

OpenMetadata 的经验说明，平台需要明确区分：

- **元数据历史版本**：当前资产的审计轨迹；
- **领域配置版本**：标准、质量、API、主数据等自身可发布的业务定义；
- **数据契约**：对当前资产结构和质量的独立、可审批预期。

三者通过稳定资产身份和事件协作，不通过“所有模块共同锁定某张表的某个版本”协作。这是控制复杂度、保持模块边界的更合适方案。

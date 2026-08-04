# 元数据架构

- 状态：架构基线 v1（已确认）
- 适用范围：技术元数据、资产目录、血缘和治理属性
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 规范化模型

平台维护自有 Canonical Metadata Model：DataSource、Database、Schema、Table、Column、File、Dataset、
MetadataSnapshot、MetadataChange、LineageEdge、Ownership、TagBinding。StandardBinding 由
`govdata-standard` 拥有，元数据只通过稳定契约和事件提供被绑定的 Asset。
JDBC、OpenMetadata、API 和文件导入均为可替换 Adapter。

## 属性所有权

- 采集属性：表名、字段类型、长度、索引、注释等，可被新快照更新。
- 治理属性：中文名、责任部门、数据管理员、密级和业务说明，不得被扫描覆盖。

外部系统 ID 通过映射保存，不作为平台主键。原始采集响应可归档用于诊断，但不作为查询事实模型。

## 采集流程

```text
创建 DiscoveryRun
→ Connector 发现
→ 转换规范化模型
→ 保存 Snapshot
→ 计算 Change
→ 更新当前视图
→ 注册/更新 Resource
→ 发布版本化事件
```

资产消失时先标记失效；破坏性结构变更通知交换、质量、标准和本体上下文。Dataset 是业务组合资源，
不等同于物理 Table。血缘使用独立关系模型，不塞入 Table JSON。

## 关键事件

`MetadataAssetDiscovered`、`MetadataSchemaChanged`、`MetadataAssetDisabled`、
`MetadataLineageChanged`。事件只携带稳定 ID、版本和必要摘要。

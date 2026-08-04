# 元数据

- 状态：领域词汇 v1
- 适用范围：元数据上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

元数据上下文保存平台规范化的技术资产事实，并区分采集属性与人工治理属性。

## Language

**Metadata Asset**:
可被识别、治理和引用的技术对象，如 Database、Schema、Table 或 Column。
_Avoid_: Resource、Dataset

**Dataset**:
面向业务组织和共享的一组数据资产，可包含多个表且不等同于物理表。
_Avoid_: Table

**Discovery Run**:
通过 Connector 对数据源执行的一次有边界、可审计的元数据发现。
_Avoid_: 同步任务

**Metadata Snapshot**:
某次发现运行观察到的资产结构状态。
_Avoid_: 当前表结构副本

**Governance Attribute**:
由治理人员维护且不得被采集覆盖的名称、责任人、密级或业务说明。
_Avoid_: 采集属性

**Lineage Edge**:
两个 Metadata Asset 之间具有方向和来源依据的数据流关系。
_Avoid_: parentId

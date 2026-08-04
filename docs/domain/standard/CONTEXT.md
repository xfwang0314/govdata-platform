# 数据标准

- 状态：领域词汇 v1
- 适用范围：数据标准上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

数据标准上下文定义数据应当符合的业务和技术规范，并管理版本与落标评估。

## Language

**Data Element**:
具有明确业务语义、数据类型、长度和约束的最小标准定义。
_Avoid_: Column、字典项

**Code Set**:
一个数据元允许使用的稳定代码及含义集合。
_Avoid_: 系统字典

**Standard Version**:
发布后不可变的一组标准定义快照。
_Avoid_: 更新时间

**Standard Binding**:
具体标准版本与 Metadata Asset 之间的落标关系。
_Avoid_: 修改字段结构

**Compliance Assessment**:
对资产是否符合绑定标准的评估结果。
_Avoid_: Quality Run

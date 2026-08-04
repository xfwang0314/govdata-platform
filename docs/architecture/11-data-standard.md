# 数据标准架构

- 状态：架构基线 v1（已确认）
- 适用范围：标准定义、发布、绑定和落标评估
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 模型

StandardCatalog、StandardDocument、DataElement、CodeSet、CodeItem、NamingRule、
ValidationRule、StandardVersion、StandardBinding、ComplianceAssessment。

标准支持国家、行业、省、市和部门层级。DataElement 表达最小标准语义，CodeSet 单独管理允许代码；
两者不得退化成普通系统字典。

## 生命周期

```text
DRAFT → REVIEWING → PUBLISHED → DEPRECATED → ARCHIVED
```

发布版本不可修改；标准升级先进行影响分析，不能自动覆盖已有绑定。StandardBinding 引用具体标准
版本和稳定 Metadata Asset ID，不直接修改数据库字段。

## 协作

- 元数据描述“现在是什么”。
- 标准描述“应该是什么”。
- 质量描述“数据内容是否达到要求”。

标准可以向质量上下文提出规则建议，质量上下文审核后形成自己的 QualityRule。落标不符合项形成
整改建议，责任人确认后再执行，标准模块不得自动修改源系统。


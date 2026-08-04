# 本体

- 状态：领域词汇 v1
- 适用范围：本体上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

本体上下文表达政务数据领域概念、关系、约束和语义映射，不承载海量业务实例。

## Language

**Ontology**:
一个命名空间下对领域概念、属性和关系的正式语义模型。
_Avoid_: Metadata Catalog、知识库

**Concept**:
领域中具有稳定含义的事物类型，如自然人或法人机构。
_Avoid_: Table、Class 文件

**Concept Relation**:
两个 Concept 之间具有语义和方向的关系。
_Avoid_: 数据库外键

**Semantic Action**:
领域中可执行能力的语义描述，可由一个或多个 MCP Tool 实现。
_Avoid_: Authorization Action、Tool 名称

**Semantic Mapping**:
Concept、属性或 Action 与元数据、标准、质量及 Tool 之间的版本化映射。
_Avoid_: 复制领域对象

**Mapping Suggestion**:
由规则或 Agent 生成、尚未人工确认的候选语义映射。
_Avoid_: 正式映射

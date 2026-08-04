# 本体架构

- 状态：架构基线 v1（已确认）
- 适用范围：数据源、元数据、交换、共享、标准、质量的语义层
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 第一版范围

第一版本体管理领域概念、属性、关系、约束、同义词、版本和语义映射，覆盖数据源、元数据、交换、
共享、标准和质量。它不存储人口、法人等海量业务实例，也不承担图推理平台。

## 模型

Ontology、OntologyVersion、Concept、ConceptProperty、RelationType、ConceptRelation、Constraint、
Term、Synonym、SemanticAction、SemanticMapping、MappingSuggestion。

## 语义映射

元数据表/字段、数据元、质量规则、交换能力和共享服务通过稳定 ID 映射到 Concept、属性或
SemanticAction。本体映射引用具体版本；本体升级必须进行映射影响分析。

Agent 可以生成 MappingSuggestion，但正式 SemanticMapping 需要人工确认和发布。MCP Tool 与
SemanticAction 通过 ToolActionBinding 关联，Tool 名称不成为本体稳定标识。

## 存储策略

第一阶段使用 MySQL 维护概念和关系。只有出现大规模实例图、复杂路径查询和推理需求时，才通过
Adapter 引入图数据库；领域接口不绑定具体图产品。


# 数据治理与语义表设计

- 状态：设计基线 v1（待评审）
- 范围：`govdata-standard`、`govdata-quality`、`govdata-ontology`
- 数据库：MySQL 8.0

## govdata-standard

标准描述“应该是什么”，不直接修改元数据或源系统。数据元和代码集不是 `sys_dict_*`。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_standard_catalog` | P0 | `catalog_id`、`catalog_code`、`catalog_name`、`parent_id`、`catalog_level`、`publisher_org`、`description`、`status` | `UK(tenant_id,catalog_code)`；父目录和层级索引 |
| `gd_standard_document` | P0 | `document_id`、`document_code`、`document_name`、`catalog_id`、`standard_level`、`standard_no`、`issuing_org`、`current_version_id`、`effective_time`、`expire_time`、`status` | `UK(tenant_id,document_code)`；标准号、目录、层级、状态索引 |
| `gd_standard_version` | P0 | `standard_version_id`、`document_id`、`version_no`、`version_label`、`source_file_ref`、`change_summary`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,document_id,version_no)`；发布后不可修改 |
| `gd_standard_data_element` | P0 | `element_id`、`standard_version_id`、`element_code`、`element_name`、`business_definition`、`data_type`、`length_value`、`precision_value`、`scale_value`、`nullable_flag`、`format_pattern`、`value_domain_type`、`code_set_id`、`classification_level` | `UK(tenant_id,standard_version_id,element_code)`；按名称、代码集、分类索引 |
| `gd_standard_code_set` | P0 | `code_set_id`、`standard_version_id`、`code_set_code`、`code_set_name`、`description`、`case_sensitive`、`status` | `UK(tenant_id,standard_version_id,code_set_code)` |
| `gd_standard_code_item` | P0 | `code_item_id`、`code_set_id`、`item_code`、`item_name`、`parent_item_id`、`display_order`、`effective_time`、`expire_time`、`status` | `UK(tenant_id,code_set_id,item_code)`；父项和排序索引 |
| `gd_standard_naming_rule` | P1 | `rule_id`、`standard_version_id`、`rule_code`、`target_type`、`pattern_type`、`pattern_value`、`case_rule`、`word_separator`、`description` | `UK(tenant_id,standard_version_id,rule_code)`；pattern 类型白名单，不执行脚本 |
| `gd_standard_validation_rule` | P1 | `rule_id`、`standard_version_id`、`rule_code`、`target_element_id`、`validation_type`、`config_json`、`severity`、`quality_rule_template_code`、`status` | `UK(tenant_id,standard_version_id,rule_code)`；只产生质量规则建议，不直接拥有 QualityRule |
| `gd_standard_binding` | P0 | `binding_id`、`standard_version_id`、`standard_object_type`、`standard_object_id`、`metadata_asset_id`、`binding_type`、`confidence`、`source_type`、`confirmed_by`、`confirmed_time`、`effective_time`、`expire_time`、`status` | 唯一落标关系；按资产、标准对象、状态索引；引用具体标准版本 |
| `gd_standard_assessment` | P0 | `assessment_id`、`assessment_no`、`binding_id`、`metadata_snapshot_id`、`assessment_type`、`result`、`score`、`difference_json`、`assessed_by_type`、`assessed_by_id`、`assessed_time`、`trace_id`、`status` | `UK(tenant_id,assessment_no)`；按绑定、结果、时间索引；不等同于 QualityRun |

## govdata-quality

规则描述检查什么，计划描述在哪里、何时以及以何阈值执行。规则可以来自标准建议，也可以完全独立创建。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_quality_rule_template` | P0 | `template_id`、`scope_type`、`scope_key`、`template_code`、`template_name`、`quality_dimension`、`rule_type`、`parameter_schema_json`、`supported_data_types_json`、`engine_code`、`risk_level`、`overridable_flag`、`status` | `UK(scope_type,scope_key,template_code)`；按总览全局注册表规则读取；模板不包含任意 SQL/脚本 |
| `gd_quality_rule` | P0 | `rule_id`、`rule_code`、`rule_name`、`quality_dimension`、`source_type`、`source_ref_id`、`template_id`、`current_version_id`、`owner_dept_id`、`status` | `UK(tenant_id,rule_code)`；来源可为 MANUAL/TEMPLATE/STANDARD/PROFILING/IMPORTED/PLUGIN |
| `gd_quality_rule_version` | P0 | `rule_version_id`、`rule_id`、`version_no`、`rule_type`、`parameter_json`、`expected_condition_json`、`severity`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,rule_id,version_no)`；发布后不可修改；复杂规则只能引用受测插件编码 |
| `gd_quality_rule_binding` | P0 | `binding_id`、`rule_version_id`、`resource_id`、`metadata_asset_id`、`metadata_snapshot_id`、`field_ids_json`、`status`、`validation_status`、`last_validated_time` | `UK(tenant_id,rule_version_id,resource_id,metadata_asset_id)`；Schema 变化后进入 `VALIDATION_REQUIRED` |
| `gd_quality_check_plan` | P0 | `plan_id`、`plan_code`、`plan_name`、`owner_dept_id`、`current_version_id`、`status`、`last_run_status`、`last_run_time` | `UK(tenant_id,plan_code)`；只保存聚合身份和当前版本指针 |
| `gd_quality_plan_version` | P0 | `plan_version_id`、`plan_id`、`version_no`、`schedule_plan_ref`、`schedule_snapshot_json`、`execution_engine`、`metadata_snapshot_strategy`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,plan_id,version_no)`；发布后调度、规则集合、阈值和权重全部冻结 |
| `gd_quality_plan_rule` | P0 | `plan_rule_id`、`plan_version_id`、`rule_binding_id`、`threshold_operator`、`threshold_value`、`weight`、`failure_action`、`display_order`、`enabled_flag` | `UK(tenant_id,plan_version_id,rule_binding_id)`；规则阈值和权重属于具体计划版本，不回写规则版本 |
| `gd_quality_run` | P0 | `run_id`、`run_no`、`plan_id`、`plan_version_id`、`metadata_snapshot_id`、`trigger_type`、`trigger_ref`、`triggered_by_type`、`triggered_by_id`、`engine_type`、`engine_job_id`、`status`、`started_time`、`completed_time`、`total_rules`、`passed_rules`、`failed_rules`、`failure_code`、`trace_id` | `UK(tenant_id,run_no)`；按计划、版本、状态、时间索引 |
| `gd_quality_metric` | P0 | `metric_id`、`run_id`、`plan_rule_id`、`resource_id`、`metric_code`、`metric_value`、`metric_unit`、`threshold_value`、`passed_flag`、`evaluated_rows`、`failed_rows`、`measured_time` | `UK(tenant_id,run_id,plan_rule_id,metric_code)`；按资源、通过状态、时间索引 |
| `gd_quality_issue` | P0 | `issue_id`、`issue_no`、`metric_id`、`rule_id`、`rule_version_id`、`resource_id`、`severity`、`issue_type`、`title`、`description`、`sample_summary_json`、`owner_dept_id`、`assignee_id`、`status`、`detected_time`、`closed_time` | `UK(tenant_id,issue_no)`；样例限量且脱敏；按资源、严重级别、责任人、状态索引 |
| `gd_quality_remediation_order` | P0 | `order_id`、`order_no`、`issue_id`、`responsible_dept_id`、`assignee_id`、`requirement`、`due_time`、`workflow_instance_id`、`status`、`submitted_time`、`verified_time` | `UK(tenant_id,order_no)`；一个问题可以多次整改，但同一时刻只允许一个活动单 |
| `gd_quality_remediation_record` | P1 | `record_id`、`order_id`、`action_type`、`operator_id`、`description`、`evidence_refs_json`、`before_status`、`after_status`、`occurred_time` | 追加写；证据使用受控文件 Resource 引用，不保存外部裸 URL |
| `gd_quality_score` | P0 | `score_id`、`score_period`、`score_scope_type`、`score_scope_id`、`quality_dimension`、`score_value`、`weight`、`metric_count`、`calculation_version`、`calculated_by_type`、`calculated_by_id`、`calculated_time`、`trace_id` | `UK(tenant_id,score_period,score_scope_type,score_scope_id,quality_dimension,calculation_version)`；评分是聚合结果，不替代 Metric |

## govdata-ontology

第一版本体只管理类型级语义，不保存人口、法人等海量实例。当前 `ry_ontology.sql` 中的
`ontology_class/property/relation/capability` 将在正式 DDL 阶段按以下统一词汇重构。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_ontology` | P0 | `ontology_id`、`ontology_code`、`ontology_name`、`namespace_uri`、`ontology_type`、`description`、`current_version_id`、`status` | `UK(tenant_id,ontology_code)`；`UK(tenant_id,namespace_uri)` |
| `gd_ontology_version` | P0 | `ontology_version_id`、`ontology_id`、`version_no`、`version_label`、`parent_version_id`、`change_summary`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,ontology_id,version_no)`；发布版本不可修改 |
| `gd_ontology_dependency` | P1 | `dependency_id`、`ontology_version_id`、`dependent_ontology_id`、`dependent_version_id`、`dependency_type`、`version_constraint` | `UK(tenant_id,ontology_version_id,dependent_ontology_id,dependent_version_id)`；禁止依赖环 |
| `gd_ontology_concept` | P0 | `concept_id`、`ontology_version_id`、`concept_code`、`concept_name`、`parent_concept_id`、`abstract_flag`、`description`、`attributes_json`、`status` | `UK(tenant_id,ontology_version_id,concept_code)`；父概念必须同版本；Concept 不等于 Metadata Table |
| `gd_ontology_concept_property` | P0 | `property_id`、`ontology_version_id`、`concept_id`、`property_code`、`property_name`、`data_type`、`required_flag`、`multi_value_flag`、`sensitive_flag`、`value_constraint_json`、`description` | `UK(tenant_id,ontology_version_id,concept_id,property_code)` |
| `gd_ontology_relation_type` | P0 | `relation_type_id`、`ontology_version_id`、`relation_code`、`relation_name`、`inverse_relation_code`、`directed_flag`、`transitive_flag`、`symmetric_flag`、`description` | `UK(tenant_id,ontology_version_id,relation_code)` |
| `gd_ontology_concept_relation` | P0 | `relation_id`、`ontology_version_id`、`source_concept_id`、`relation_type_id`、`target_concept_id`、`cardinality`、`description` | `UK(tenant_id,ontology_version_id,source_concept_id,relation_type_id,target_concept_id)`；源目标反查索引 |
| `gd_ontology_constraint` | P1 | `constraint_id`、`ontology_version_id`、`target_type`、`target_id`、`constraint_type`、`constraint_json`、`severity`、`message_template`、`status` | 约束 DSL 白名单；不执行 SQL、SpEL 或脚本 |
| `gd_ontology_term` | P0 | `term_id`、`ontology_version_id`、`term_code`、`preferred_label`、`language_code`、`definition`、`concept_id`、`status` | `UK(tenant_id,ontology_version_id,term_code,language_code)`；按首选词和概念索引 |
| `gd_ontology_synonym` | P0 | `synonym_id`、`term_id`、`synonym_text`、`language_code`、`synonym_type`、`source_type`、`confidence`、`status` | `UK(tenant_id,term_id,synonym_text,language_code)` |
| `gd_ontology_semantic_action` | P1 | `semantic_action_id`、`ontology_version_id`、`action_code`、`action_name`、`target_concept_id`、`input_constraint_json`、`output_constraint_json`、`risk_level`、`description`、`status` | `UK(tenant_id,ontology_version_id,action_code)`；与授权 Action 和 MCP Tool 分离 |
| `gd_ontology_semantic_mapping` | P0 | `mapping_id`、`ontology_version_id`、`semantic_target_type`、`semantic_target_id`、`source_module`、`source_object_type`、`source_object_id`、`mapping_type`、`confidence`、`evidence_json`、`confirmed_by`、`confirmed_time`、`effective_time`、`status` | 唯一正式映射；引用具体本体版本和外部稳定 ID；按两端对象索引 |
| `gd_ontology_mapping_suggestion` | P1 | `suggestion_id`、`ontology_version_id`、`semantic_target_type`、`semantic_target_id`、`source_module`、`source_object_type`、`source_object_id`、`suggestion_source`、`confidence`、`evidence_json`、`status`、`reviewed_by`、`reviewed_time`、`rejection_reason` | 候选映射与正式映射隔离；规则或 Agent 生成结果必须人工确认 |
| `gd_ontology_impact_analysis` | P1 | `analysis_id`、`source_version_id`、`target_version_id`、`affected_mapping_count`、`breaking_change_count`、`result_summary_json`、`status`、`started_time`、`completed_time` | `UK(tenant_id,source_version_id,target_version_id)`；发布新版本前完成影响分析 |

### 与现有本体 SQL 的关系

- 使用 `Concept`，不使用易与 Java/面向对象混淆的 `Class`。
- 使用独立 `RelationType + ConceptRelation`，避免把关系定义和具体概念关系混成一张表。
- `SemanticAction` 不直接保存 `mcp_tool_id`；映射由
  `platform_mcp_tool_action_binding` 维护。
- 不使用 `AUTO_INCREMENT`，所有内部主键统一由平台雪花 ID 生成。

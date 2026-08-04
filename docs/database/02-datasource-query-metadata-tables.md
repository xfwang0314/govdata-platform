# 数据接入与目录表设计

- 状态：设计基线 v1（待评审）
- 范围：`govdata-datasource`、`govdata-query`、`govdata-metadata`
- 数据库：MySQL 8.0

## govdata-datasource

Connector 类型、配置 Schema 和 Capability 由 SPI 实现声明，运行时汇总，不把 Java 插件注册表
复制到数据库。数据库只保存租户的数据源实例及运行事实。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_datasource` | P0 | `datasource_id`、`datasource_code`、`datasource_name`、`connector_code`、`connector_version`、`endpoint_config_json`、`secret_ref`、`environment`、`owner_dept_id`、`readonly_flag`、`status`、`last_test_status`、`last_test_time` | `UK(tenant_id,datasource_code)`；按 Connector、状态、权属部门索引；配置必须符合 Connector Schema |
| `gd_datasource_capability_override` | P1 | `override_id`、`datasource_id`、`capability_code`、`enabled_flag`、`limit_config_json`、`reason` | `UK(tenant_id,datasource_id,capability_code)`；只能收紧 Connector 声明能力，不能伪造插件未实现能力 |
| `gd_datasource_connection_test` | P0 | `test_id`、`datasource_id`、`connector_code`、`connector_version`、`result`、`reason_code`、`latency_ms`、`server_info_json`、`actor_type`、`actor_id`、`trace_id`、`occurred_time` | 追加写；服务端信息脱敏；按数据源、结果、时间索引 |
| `gd_datasource_health_snapshot` | P1 | `snapshot_id`、`datasource_id`、`health_status`、`latency_ms`、`pool_active`、`pool_idle`、`failure_code`、`actor_type`、`actor_id`、`trace_id`、`checked_time` | 追加写并按保留期清理；定时采样使用 `actor_type=SYSTEM`；按数据源和检查时间索引 |

`endpoint_config_json` 只保存 host、port、database、basePath、参数等非秘密配置；密码、Token、私钥
只能通过 `secret_ref` 获取。Redis、SFTP 不伪装为 JDBC 数据源。

## govdata-query

查询网关不保存任意 SQL，也不建设“万能查询条件表”。AuthorizedQueryPlan 是请求模型；只持久化
安全审计和异步导出状态。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_query_access_log` | P0 | `access_id`、`request_id`、`actor_type`、`actor_id`、`datasource_id`、`resource_id`、`purpose`、`field_ids_json`、`filter_fingerprint`、`query_fingerprint`、`decision_id`、`masking_summary_json`、`returned_rows`、`result_bytes`、`duration_ms`、`result`、`reason_code`、`trace_id`、`occurred_time` | 追加写；不保存原始 SQL、参数敏感值或结果集；按资源、主体、数据源、结果、时间索引 |
| `gd_query_export_job` | P1 | `export_id`、`export_no`、`requester_type`、`requester_id`、`datasource_id`、`resource_id`、`purpose`、`authorized_plan_json`、`decision_id`、`status`、`max_rows`、`expire_time`、`started_time`、`completed_time`、`failure_code`、`trace_id` | `UK(tenant_id,export_no)`；独立导出权限；计划只保存结构化稳定 ID 和已脱敏参数 |
| `gd_query_export_artifact` | P1 | `artifact_id`、`export_id`、`file_resource_id`、`storage_object_ref`、`file_name`、`content_type`、`file_size`、`checksum`、`expire_time`、`status` | `UK(tenant_id,export_id)`；下载仍需 Resource Grant；不保存对象存储永久明文地址 |

## govdata-metadata

### 资产当前视图

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_metadata_asset` | P0 | `asset_id`、`asset_code`、`asset_type`、`parent_asset_id`、`datasource_id`、`external_key`、`technical_name`、`display_name`、`business_name`、`description`、`owner_dept_id`、`steward_user_id`、`classification_level`、`security_level`、`technical_status`、`governance_status`、`first_seen_time`、`last_seen_time`、`structure_hash` | `UK(tenant_id,asset_code)`；`UK(tenant_id,datasource_id,asset_type,external_key)`；父级、类型状态、权属索引 |
| `gd_metadata_database` | P0 | `asset_id`、`catalog_name`、`database_product`、`product_version`、`default_charset`、`default_collation`、`properties_json` | `asset_id` 同时为 PK；仅数据库级规范化技术属性 |
| `gd_metadata_schema` | P0 | `asset_id`、`schema_name`、`schema_owner`、`properties_json` | `asset_id` 同时为 PK；父资产必须是 Database 或等价 Catalog |
| `gd_metadata_table` | P0 | `asset_id`、`table_type`、`row_estimate`、`data_size_bytes`、`engine_name`、`partitioned_flag`、`comment_text`、`properties_json` | `asset_id` 同时为 PK；按表类型索引 |
| `gd_metadata_column` | P0 | `asset_id`、`ordinal_position`、`data_type`、`native_type`、`length_value`、`precision_value`、`scale_value`、`nullable_flag`、`primary_key_flag`、`unique_flag`、`auto_increment_flag`、`default_expression`、`comment_text` | `asset_id` 同时为 PK；`UK(tenant_id,parent_asset_id,technical_name)` 由资产表保证；父表和顺序索引 |
| `gd_metadata_file` | P0 | `asset_id`、`storage_provider`、`storage_object_ref`、`original_file_name`、`content_type`、`file_format`、`file_size`、`checksum`、`expire_time`、`properties_json` | `asset_id` 同时为 PK；拥有 FileResource 业务事实；存储对象可复用 `sys_oss` 稳定引用，不保存永久裸下载地址 |

`gd_metadata_asset` 保存所有资产共有的身份、层级和治理属性；四张明细表只保存各类型技术字段。
采集只能更新技术字段和技术状态，不能覆盖人工维护的业务名称、责任人、分类和密级。

### Dataset 与关系

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_metadata_dataset` | P0 | `dataset_id`、`dataset_code`、`dataset_name`、`description`、`owner_dept_id`、`steward_user_id`、`classification_level`、`security_level`、`status` | `UK(tenant_id,dataset_code)`；Dataset 不等于 Table，也不伪装成资产明细表 |
| `gd_metadata_dataset_asset` | P0 | `binding_id`、`dataset_id`、`asset_id`、`usage_role`、`display_order`、`effective_time`、`expire_time` | `UK(tenant_id,dataset_id,asset_id,usage_role)`；数据集和资产反查索引 |
| `gd_metadata_lineage_edge` | P1 | `edge_id`、`source_asset_id`、`target_asset_id`、`lineage_type`、`source_type`、`source_ref`、`confidence`、`evidence_json`、`effective_time`、`status` | 唯一边 `(tenant_id,source_asset_id,target_asset_id,lineage_type,source_type,source_ref)`；源、目标分别索引 |
| `gd_metadata_tag` | P1 | `tag_id`、`tag_code`、`tag_name`、`tag_category`、`color`、`description`、`status` | `UK(tenant_id,tag_code)`；系统分类标签与普通展示标签分开类别 |
| `gd_metadata_tag_binding` | P1 | `binding_id`、`tag_id`、`target_type`、`target_id`、`source_type`、`confidence`、`confirmed_by`、`confirmed_time` | `UK(tenant_id,tag_id,target_type,target_id)`；支持资产与 Dataset；自动标签需保留来源和确认状态 |

### 发现、快照与变化

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_metadata_discovery_run` | P0 | `run_id`、`run_no`、`datasource_id`、`connector_code`、`connector_version`、`scope_json`、`trigger_type`、`trigger_ref`、`triggered_by_type`、`triggered_by_id`、`status`、`started_time`、`completed_time`、`discovered_count`、`changed_count`、`failure_code`、`trace_id` | `UK(tenant_id,run_no)`；按数据源、状态、开始时间索引 |
| `gd_metadata_snapshot` | P0 | `snapshot_id`、`datasource_id`、`run_id`、`snapshot_no`、`asset_count`、`content_hash`、`raw_archive_ref`、`created_time` | `UK(tenant_id,run_id)`；`UK(tenant_id,datasource_id,snapshot_no)`；快照不可修改；原始响应只存归档引用 |
| `gd_metadata_snapshot_asset` | P0 | `snapshot_asset_id`、`snapshot_id`、`asset_id`、`external_key`、`asset_type`、`parent_external_key`、`structure_hash`、`normalized_definition_json` | `UK(tenant_id,snapshot_id,asset_type,external_key)`；JSON 是某次观察快照，不作为当前查询事实 |
| `gd_metadata_change` | P0 | `change_id`、`datasource_id`、`asset_id`、`previous_snapshot_id`、`current_snapshot_id`、`change_type`、`breaking_flag`、`before_hash`、`after_hash`、`change_summary_json`、`status`、`detected_time` | 按资产、破坏性标志、状态、检测时间索引；用于通知交换、质量、标准和本体 |
| `gd_metadata_external_mapping` | P1 | `mapping_id`、`asset_id`、`external_system`、`external_type`、`external_id`、`external_fqn`、`sync_version`、`last_sync_time` | `UK(tenant_id,external_system,external_type,external_id)`；外部 ID 不作为平台主键 |

### 不单独建表的内容

- Ownership 的当前值由 `gd_metadata_asset.owner_dept_id` 和 `steward_user_id` 保存；需要多人协作时再新增
  Assignment 表，第一版不提前建设。
- StandardBinding 归 `govdata-standard` 所有，元数据只通过 API/事件读取状态。
- Resource 投影归 `platform-auth` 所有，元数据不复制 `authz_resource`。

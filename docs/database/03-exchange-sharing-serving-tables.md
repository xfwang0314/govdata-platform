# 数据流通表设计

- 状态：设计基线 v1（待评审）
- 范围：`govdata-exchange`、`govdata-sharing`、`govdata-serving`
- 数据库：MySQL 8.0

## govdata-exchange

平台保存交换控制面事实，SeaTunnel 保存执行引擎细节。业务表不保存 HOCON 和凭据明文。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_exchange_job` | P0 | `job_id`、`job_code`、`job_name`、`job_type`、`owner_dept_id`、`current_version_id`、`schedule_plan_id`、`status`、`last_run_status`、`last_run_time` | `UK(tenant_id,job_code)`；按状态、权属、任务类型索引 |
| `gd_exchange_job_version` | P0 | `job_version_id`、`job_id`、`version_no`、`description`、`execution_mode`、`schema_snapshot_json`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,job_id,version_no)`；发布后不可修改；Schema 引用稳定资产 ID |
| `gd_exchange_pipeline` | P0 | `pipeline_id`、`job_version_id`、`pipeline_code`、`pipeline_name`、`source_datasource_id`、`source_resource_id`、`sink_datasource_id`、`sink_resource_id`、`write_mode`、`checkpoint_enabled`、`parallelism`、`pipeline_order` | `UK(tenant_id,job_version_id,pipeline_code)`；数据源和资源索引；不保存 SecretValue |
| `gd_exchange_field_mapping` | P0 | `mapping_id`、`pipeline_id`、`source_field_id`、`target_field_id`、`target_field_name_snapshot`、`transform_type`、`transform_config_json`、`nullable_flag`、`mapping_order` | `UK(tenant_id,pipeline_id,target_field_id)`；字段必须来自已发布元数据快照；转换类型白名单 |
| `gd_exchange_schedule_plan` | P0 | `schedule_plan_id`、`plan_code`、`trigger_type`、`cron_expression`、`timezone`、`misfire_policy`、`concurrency_policy`、`scheduler_ref`、`effective_time`、`expire_time`、`status` | `UK(tenant_id,plan_code)`；调度器引用不是领域主键 |
| `gd_exchange_run` | P0 | `run_id`、`run_no`、`job_id`、`job_version_id`、`trigger_type`、`trigger_ref`、`triggered_by_type`、`triggered_by_id`、`engine_type`、`engine_job_id`、`engine_job_version`、`status`、`started_time`、`completed_time`、`checkpoint_ref`、`input_rows`、`output_rows`、`rejected_rows`、`failure_code`、`trace_id` | `UK(tenant_id,run_no)`；`UK(tenant_id,engine_type,engine_job_id)`；按任务、版本、状态、时间索引 |
| `gd_exchange_run_metric` | P1 | `metric_id`、`run_id`、`pipeline_id`、`metric_code`、`metric_value`、`metric_unit`、`collected_time` | `UK(tenant_id,run_id,pipeline_id,metric_code,collected_time)`；追加写并按保留期归档 |
| `gd_exchange_run_event` | P1 | `event_id`、`run_id`、`event_type`、`event_level`、`engine_status`、`reason_code`、`message_summary`、`occurred_time`、`trace_id` | 追加写；不复制完整 SeaTunnel 日志；按运行、级别、时间索引 |

Schema 破坏性变化命中已发布版本时，任务进入 `VALIDATION_REQUIRED`，不能静默改写版本。

## govdata-sharing

共享只发生在同一租户的部门之间。审批事实、共享协议和运行时 Grant 分开建模。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_sharing_catalog_item` | P0 | `item_id`、`item_code`、`item_name`、`resource_type`、`resource_id`、`provider_dept_id`、`description`、`application_required`、`default_valid_days`、`delivery_modes_json`、`classification_level`、`status` | `UK(tenant_id,item_code)`；同一资源可有多个面向不同用途的目录项；按资源、提供部门、状态索引 |
| `gd_sharing_request` | P0 | `request_id`、`request_no`、`catalog_item_id`、`applicant_id`、`consumer_dept_id`、`purpose_code`、`purpose_description`、`delivery_mode`、`requested_effective_time`、`requested_expire_time`、`field_scope_json`、`row_scope_json`、`workflow_instance_id`、`status` | `UK(tenant_id,request_no)`；申请人、使用部门、目录项、状态、时间索引；字段引用稳定字段 ID |
| `gd_sharing_approval` | P0 | `approval_id`、`request_id`、`approval_level`、`approver_type`、`approver_id`、`decision`、`opinion`、`workflow_task_id`、`decided_time` | 追加写；`UK(tenant_id,workflow_task_id)`；保存审批人/部门名称快照 |
| `gd_sharing_agreement` | P0 | `agreement_id`、`agreement_no`、`request_id`、`provider_dept_id`、`consumer_dept_id`、`purpose`、`effective_time`、`expire_time`、`terms_json`、`status`、`signed_time` | `UK(tenant_id,agreement_no)`；`UK(tenant_id,request_id)`；按双方部门、状态、有效期索引 |
| `gd_sharing_agreement_scope` | P0 | `scope_id`、`agreement_id`、`resource_id`、`action_code`、`field_scope_json`、`row_scope_json`、`max_rows`、`export_allowed` | `UK(tenant_id,agreement_id,resource_id,action_code)`；共享约束是业务事实，激活时转换为 Grant Obligation |
| `gd_sharing_subscription` | P0 | `subscription_id`、`subscription_no`、`agreement_id`、`catalog_item_id`、`subscriber_type`、`subscriber_id`、`delivery_mode`、`effective_time`、`expire_time`、`status` | `UK(tenant_id,subscription_no)`；表达共享业务订阅，不复制数据服务运行时订阅或 Grant；按目录项、主体、有效期索引 |
| `gd_sharing_delivery_plan` | P0 | `delivery_plan_id`、`agreement_id`、`delivery_mode`、`target_ref_type`、`target_ref_id`、`exchange_job_id`、`service_subscription_id`、`file_resource_id`、`activation_request_id`、`status`、`activated_time` | 每个交付方式只使用对应引用；`activation_request_id` 唯一保证幂等 |
| `gd_sharing_usage_record` | P1 | `usage_id`、`agreement_id`、`subscription_id`、`consumer_type`、`consumer_id`、`resource_id`、`usage_type`、`purpose`、`row_count`、`result_bytes`、`access_log_id`、`occurred_time`、`trace_id` | 追加写；保存用量摘要，不复制查询结果；按协议、订阅、主体、资源、时间索引 |
| `gd_sharing_revocation` | P0 | `revocation_id`、`revocation_no`、`agreement_id`、`reason_code`、`reason`、`requested_by`、`requested_time`、`workflow_instance_id`、`grant_revoke_request_id`、`status`、`completed_time` | `UK(tenant_id,revocation_no)`；Grant 禁用和缓存清理确认后才能完成 |

生命周期约束：`APPROVED` 只代表审批通过；`AuthorizationGrant` 创建确认后才能进入 `ACTIVE`。
`gd_sharing_subscription` 是共享业务事实；API 交付产生的运行时订阅归
`gd_serving_subscription` 所有，两者通过 `gd_sharing_delivery_plan.service_subscription_id` 关联。

## govdata-serving

数据服务是面向消费者发布的数据 API，不等同于 Java 模块间的 `govdata-api`。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `gd_serving_service` | P0 | `service_id`、`service_code`、`service_name`、`owner_dept_id`、`current_version_id`、`description`、`classification_level`、`status` | `UK(tenant_id,service_code)`；按权属部门和状态索引 |
| `gd_serving_service_version` | P0 | `service_version_id`、`service_id`、`version_no`、`protocol_type`、`response_mode`、`timeout_ms`、`max_rows`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,service_id,version_no)`；发布后不可修改；破坏性变化必须新版本 |
| `gd_serving_input_parameter` | P0 | `parameter_id`、`service_version_id`、`parameter_code`、`parameter_name`、`data_type`、`required_flag`、`default_value_json`、`validation_json`、`binding_field_id`、`display_order` | `UK(tenant_id,service_version_id,parameter_code)`；禁止 SQL 片段参数 |
| `gd_serving_output_field` | P0 | `field_id`、`service_version_id`、`output_code`、`output_name`、`source_field_id`、`data_type`、`nullable_flag`、`masking_type`、`display_order` | `UK(tenant_id,service_version_id,output_code)`；字段必须来自元数据或受信任派生定义 |
| `gd_serving_query_definition` | P0 | `query_definition_id`、`service_version_id`、`primary_resource_id`、`query_plan_json`、`required_action_code`、`purpose_code`、`definition_hash` | `UK(tenant_id,service_version_id)`；只存结构化查询计划，不存原始 SQL |
| `gd_serving_endpoint` | P0 | `endpoint_id`、`service_version_id`、`endpoint_code`、`http_method`、`path_pattern`、`gateway_route_id`、`published_url`、`effective_time`、`expire_time`、`status` | 路径唯一 `UK(tenant_id,http_method,path_pattern,status)`；外部 URL 不是服务稳定标识 |
| `gd_serving_rate_limit_policy` | P1 | `rate_policy_id`、`service_version_id`、`dimension_type`、`limit_count`、`window_seconds`、`burst_capacity`、`concurrency_limit`、`status` | `UK(tenant_id,service_version_id,dimension_type)`；运行时可投影到网关/Redis |
| `gd_serving_subscription` | P0 | `subscription_id`、`subscription_no`、`service_id`、`service_version_id`、`subscriber_type`、`subscriber_id`、`sharing_agreement_id`、`grant_id`、`quota_json`、`effective_time`、`expire_time`、`status` | `UK(tenant_id,subscription_no)`；服务、主体、Grant 和有效期索引 |
| `gd_serving_invocation` | P0 | `invocation_id`、`request_id`、`endpoint_id`、`service_id`、`service_version_id`、`subscription_id`、`caller_type`、`caller_id`、`resource_id`、`decision_id`、`input_fingerprint`、`returned_rows`、`result_bytes`、`http_status`、`result`、`reason_code`、`duration_ms`、`trace_id`、`occurred_time` | 追加写；不保存 Token、完整输入或响应；按服务、订阅、调用方、结果、时间索引 |

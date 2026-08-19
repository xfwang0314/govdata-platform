# 平台通用能力表设计

- 状态：设计基线 v1（待评审）
- 范围：`platform-auth`、`platform-secret`、`platform-event`、`platform-mcp`
- 数据库：MySQL 8.0
- 最后更新：2026-08-02

除特别说明外，业务表都带 `tenant_id`；跨租户应急访问不复用普通 Grant。

## platform-auth

第一版建表范围固定为 `authz_resource`、`authz_resource_relation`、`authz_policy_group`、
`authz_policy_group_version`、`authz_policy_runtime_rule`、`authz_policy_runtime_subject`、
`authz_policy_runtime_resource`、`authz_policy_operation_log`、`authz_grant`、`authz_grant_scope`、
`authz_decision_log`。其余表保留完整设计，但不进入第一版执行 DDL。

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `authz_resource` | P0 | `resource_id`、`resource_code`、`resource_type`、`parent_id`、`source_module`、`business_id`、`owner_dept_id`、`region_code`、`classification_code`、`security_level`、`attributes_json`、`inherit_policy`、`status` | `UK(tenant_id,resource_code)`；`UK(tenant_id,source_module,resource_type,business_id)`；索引父资源、权属部门、类型状态 |
| `authz_resource_relation` | P0 | `relation_id`、`source_resource_id`、`target_resource_id`、`relation_type` | `UK(tenant_id,source_resource_id,target_resource_id,relation_type)`；反向查询索引 |
| `authz_attribute_schema` | P1 | `schema_id`、`scope_type`、`scope_key`、`attribute_code`、`namespace`、`applies_to`、`data_type`、`allowed_values_json`、`required_flag`、`overridable_flag`、`status` | `UK(scope_type,scope_key,attribute_code)`；按总览的全局注册表规则读取；策略发布只允许引用已启用属性 |
| `platform_service_principal` | P0 | `service_id`、`service_code`、`service_name`、`owner_dept_id`、`client_id`、`secret_ref`、`purpose_scope`、`effective_time`、`expire_time`、`status` | `UK(tenant_id,service_code)`；`UK(tenant_id,client_id)`；SERVICE 使用独立客户端身份，Grant/Delegation 的 SERVICE ID 引用 `service_id` |
| `authz_policy_group` | P0 | `policy_group_id`、`policy_group_code`、`status`、`current_version_id`、`draft_version_id`、`next_version_no`、`creation_source`、删除审计、`lock_version` | 策略唯一管理聚合；`UK(tenant_id,policy_group_code)`；聚合状态仅 `DRAFT/ACTIVE/DISABLED`；一个组最多一个待发布版本，`draft_version_id` 为兼容字段名 |
| `authz_policy_group_version` | P0 | `policy_group_version_id`、`policy_group_id`、`version_no`、`schema_version`、`policy_document_json`、`version_status`、`content_hash`、目录快照、发布审计 | `UK(tenant_id,policy_group_id,version_no)`；完整文档是唯一不可变策略事实；版本状态仅 `DRAFT/PUBLISHED/SUPERSEDED/DISCARDED`，其中 DRAFT 对用户展示为“待发布” |
| `authz_policy_runtime_rule` | P0 | `runtime_rule_id`、组与版本 ID、`rule_code`、`effect`、`priority`、`resource_type`、`resource_scope`、动作、目的、条件、义务 | 发布时由文档重建；按资源类型、效果和优先级筛选候选规则 |
| `authz_policy_runtime_subject` | P0 | `runtime_subject_id`、组与版本 ID、`subject_type`、`subject_id`、`include_children` | 发布时由文档重建；主体选择器按 OR 匹配；部门下级继承必须显式开启 |
| `authz_policy_runtime_resource` | P0 | `runtime_resource_id`、`runtime_rule_id`、`resource_id`、`include_children` | 仅保存实例型规则目标；类型型规则不制造虚拟资源投影 |
| `authz_policy_operation_log` | P0 | `operation_log_id`、`policy_group_id`、组编码快照、版本 ID、操作类型、状态前后值、原因、内容哈希、操作主体、`trace_id`、`occurred_time` | 生命周期操作追加写；按策略组、版本、traceId 和时间查询；不与授权决策日志混用 |
| `authz_grant` | P0 | `grant_id`、`grant_code`、`source_type`、`source_id`、`grantor_type`、`grantor_id`、`grantee_type`、`grantee_id`、`purpose`、`effective_time`、`expire_time`、`condition_json`、`status`、`revoked_time` | `UK(tenant_id,grant_code)`；来源幂等键 `UK(tenant_id,source_type,source_id)`；主体和有效期索引；禁止跨租户 |
| `authz_grant_scope` | P0 | `scope_id`、`grant_id`、`resource_id`、`action_code`、`include_children`、`obligation_json` | `UK(tenant_id,grant_id,resource_id,action_code)`；资源反查索引 |
| `authz_delegation` | P2 | `delegation_id`、`delegation_no`、`delegator_type`、`delegator_id`、`agent_id`、`agent_task_id`、`purpose`、`effective_time`、`expire_time`、`condition_json`、`status`、`source_grant_id` | 仅 `HUMAN/SERVICE → AGENT`；任务、Agent、有效期索引；不保存用户 Token |
| `authz_delegation_scope` | P2 | `scope_id`、`delegation_id`、`resource_id`、`action_code`、`include_children`、`obligation_json` | 委托范围只能缩小，不能超过委托人和 Agent 基础能力交集 |
| `authz_break_glass_access` | P2 | `access_id`、`request_no`、`requester_id`、`source_tenant_id`、`target_tenant_id`、`reason`、`readonly_flag`、`workflow_instance_id`、`effective_time`、`expire_time`、`status` | 与普通 Grant 隔离；必须审批、限时、默认只读；请求号唯一；按目标租户和有效期索引 |
| `authz_break_glass_scope` | P2 | `scope_id`、`access_id`、`target_tenant_id`、`resource_id`、`action_code`、`obligation_json` | `UK(tenant_id,access_id,resource_id,action_code)`；目标租户必须与 Access 一致；禁止用 JSON 代替资源动作关系 |
| `authz_decision_log` | P0 | `decision_id`、`subject_type`、`subject_id`、主体快照、`resource_id`、`resource_type_snapshot`、`resource_code_snapshot`、`action_code`、`decision`、`reason_code`、`policy_version_ids_json`、`grant_ids_json`、`delegation_id`、`obligation_summary_json`、`purpose`、`trace_id`、`duration_ms`、`occurred_time` | 追加写；按主体、资源、结果、时间和 traceId 查询；不保存敏感属性原值 |

### 关键取舍

- 当前可执行结构以 `script/sql/baseline/20_platform_auth.sql` 为准，策略采用
  `authz_policy_group + authz_policy_group_version` 不可变版本模型。
- 策略版本完整文档是唯一事实来源；三张 `authz_policy_runtime_*` 表是发布时可重建的运行时索引；
  操作日志是独立追加事实。
- Policy、Grant、Delegation 的动作均使用关系行，不再保存逗号分隔动作。
- `authz_resource.attributes_json` 只允许保存 `authz_attribute_schema` 注册过的扩展属性。
- Grant 保存业务来源引用，但不复制 SharingAgreement、Subscription 或审批详情。
- Grant/Delegation 进入 `ACTIVE` 后 Scope 不可修改；扩大或缩小范围都通过撤销旧记录并创建新记录完成。

## platform-secret

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `platform_secret` | P0 | `secret_id`、`secret_ref`、`secret_name`、`description`、`secret_type`、`owner_module`、`purpose_scope`、`current_version_id`、`status`、`expire_time` | `UK(tenant_id,secret_ref)`；按模块、类型、状态索引；description 只保存非敏感管理说明；禁止任何明文字段 |
| `platform_secret_version` | P0 | `secret_version_id`、`secret_id`、`version_no`、`ciphertext`、`encryption_algorithm`、`key_version`、`checksum`、`effective_time`、`expire_time`、`status`、创建审计 | `UK(tenant_id,secret_id,version_no)`；密钥不在数据库；旧版本只禁用不覆盖 |
| `platform_secret_access_log` | P0 | `access_log_id`、`secret_id`、`secret_ref_snapshot`、`caller_module`、`actor_type`、`actor_id`、`purpose`、`result`、`reason_code`、`trace_id`、`occurred_time` | 追加写；不保存 SecretValue；按 Secret、调用模块、主体、结果、时间索引 |

## platform-event

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `platform_outbox_event` | P0 | `event_id`、`event_type`、`event_version`、`aggregate_type`、`aggregate_id`、`payload_json`、`trace_id`、`occurred_time`、`publish_status`、`retry_count`、`next_retry_time`、`published_time` | `event_id` 全局唯一；发布扫描索引 `(publish_status,next_retry_time,occurred_time)`；与业务事务同库提交 |
| `platform_event_consume_record` | P0 | `record_id`、`event_id`、`consumer_name`、`event_type`、`consume_status`、`attempt_count`、`first_receive_time`、`last_attempt_time`、`completed_time`、`error_code` | `UK(consumer_name,event_id)` 保证幂等；按消费状态和重试时间索引 |
| `platform_event_dead_letter` | P1 | `dead_letter_id`、`event_id`、`consumer_name`、`payload_snapshot_json`、`failure_code`、`failure_message`、`attempt_count`、`status`、`replay_by`、`replay_time` | `UK(consumer_name,event_id)`；错误信息脱敏；支持人工重放与关闭 |

## platform-mcp 与 Agent

| 表名 | 阶段 | 关键字段 | 约束与主要索引 |
|---|---|---|---|
| `platform_agent` | P2 | `agent_id`、`agent_code`、`agent_name`、`owner_dept_id`、`agent_type`、`current_version_id`、`risk_level`、`status` | `UK(tenant_id,agent_code)`；`agent_id` 本身就是 AGENT Principal ID，不绑定人员 Token |
| `platform_agent_version` | P2 | `agent_version_id`、`agent_id`、`version_no`、`model_provider`、`model_name`、`system_instruction_hash`、`tool_policy_json`、`runtime_config_json`、`content_hash`、`version_status`、发布审计 | `UK(tenant_id,agent_id,version_no)`；发布后不可修改；不保存模型服务永久 Secret |
| `platform_agent_capability` | P2 | `capability_id`、`agent_version_id`、`action_code`、`resource_type`、`condition_json`、`effective_time`、`expire_time`、`status` | Agent 版本的基础能力上限；唯一能力范围；发布后冻结，不得代替运行时 Delegation |
| `platform_agent_task` | P2 | `task_id`、`task_no`、`agent_id`、`agent_version_id`、`delegator_type`、`delegator_id`、`delegation_id`、`purpose`、`input_summary_json`、`risk_level`、`effective_time`、`expire_time`、`status`、`started_time`、`completed_time`、`trace_id` | `UK(tenant_id,task_no)`；按 Agent、状态、有效期索引；不保存完整敏感对话 |
| `platform_agent_task_scope` | P2 | `scope_id`、`task_id`、`resource_id`、`action_code`、`include_children`、`requested_obligation_json` | `UK(tenant_id,task_id,resource_id,action_code)`；记录任务请求范围，实际权限仍取任务范围与 Delegation、Agent 能力、ABAC 的交集 |
| `platform_mcp_tool` | P2 | `tool_id`、`scope_type`、`scope_key`、`tool_code`、`tool_name`、`provider_module`、`current_version_id`、`risk_level`、`confirmation_mode`、`status` | `UK(scope_type,scope_key,tool_code)`；按总览全局注册表规则读取；Tool 名称不是语义 Action ID |
| `platform_mcp_tool_version` | P2 | `tool_version_id`、`tool_id`、`version_no`、`input_schema_json`、`output_schema_json`、`timeout_ms`、`idempotent_flag`、`content_hash`、发布审计 | `UK(tool_id,version_no)`；发布后不可修改 |
| `platform_mcp_tool_action_binding` | P2 | `binding_id`、`tool_version_id`、`semantic_action_id`、`authorization_action_code`、`input_concept_id`、`output_concept_id`、`status` | Tool、语义 Action、授权 Action 分离；唯一绑定；跨模块仅保存稳定 ID |
| `platform_mcp_invocation_log` | P2 | `invocation_id`、`task_id`、`agent_id`、`delegator_id`、`tool_id`、`tool_version_id`、`resource_ids_json`、`decision_id`、`result`、`reason_code`、`duration_ms`、`trace_id`、`occurred_time` | 追加写；输入输出只保存摘要、Schema 版本和哈希；按任务、Tool、结果、时间索引 |

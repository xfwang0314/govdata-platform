# 稳定业务编码前缀登记表

- 状态：v1（强制）
- 所有者：GovData Platform Team
- 最后更新：2026-08-04

稳定业务编码由后端在创建聚合时生成，格式统一为 `{prefix}_{snowflake-base36}`。
Command、表单和外部调用方不得传入或修改该编码；删除后不得复用。

| 前缀 | 所有者模块 | 实体 | 字段 |
|---|---|---|---|
| `res_` | platform-auth | 授权资源 | `resource_code` |
| `pol_` | platform-auth | ABAC 策略 | `policy_code` |
| `grt_` | platform-auth | 运行时 Grant | `grant_code` |
| `sec_` | platform-secret | 凭据 | `secret_code` |
| `ds_` | govdata-datasource | 数据源 | `datasource_code` |
| `ont_` | govdata-ontology（预留） | 本体 | `ontology_code` |
| `onc_` | govdata-ontology（预留） | 本体概念/类 | `concept_code` 或迁移前预留字段 |

新增前缀必须先修改本表并完成命名冲突检查。法定编号、标准编号等用户可识别业务编号使用独立字段，
不得占用系统稳定编码。

# MySQL 8 Upgrade 执行清单

- 状态：v1
- 适用：已部署环境；按未应用顺序执行
- 最后更新：2026-08-23

执行前记录目标环境、操作者、开始/结束时间、脚本 SHA-256、结果与 `traceId`（如有）。每个脚本开头的前置、锁表风险、验证和回滚说明同样是执行条件。

| 顺序 | 文件 | 模块 |
|---|---|---|
| 01 | `20260801_001_remove_ruoyi_generator.sql` | 平台初始化 |
| 02 | `20260802_002_remove_platform_auth_foreign_keys.sql` | platform-auth |
| 03 | `20260803_001_create_govdata_datasource.sql` | govdata-datasource |
| 04 | `20260803_002_remove_snailjob.sql` | 上游运行组件清理 |
| 05 | `20260803_003_extend_datasource_management.sql` | govdata-datasource |
| 06 | `20260803_004_add_platform_auth_menus.sql` | platform-auth |
| 07 | `20260804_001_add_platform_secret_menus.sql` | platform-secret |
| 08 | `20260805_001_add_platform_secret_description.sql` | platform-secret |
| 09 | `20260809_001_add_auth_profile_code_to_gd_datasource.sql` | govdata-datasource |
| 10 | `20260809_002_add_datasource_metadata_menus.sql` | govdata-datasource |
| 11 | `20260810_001_align_platform_auth_catalog.sql` | platform-auth |
| 12 | `20260810_002_add_auth_decision_query_permission.sql` | platform-auth |
| 13 | `20260811_001_complete_authz_policy_lifecycle.sql` | platform-auth |
| 14 | `20260811_002_add_authz_resource_ownership.sql` | platform-auth |
| 15 | `20260811_003_add_authz_policy_group.sql` | platform-auth |
| 16 | `20260811_004_create_authz_policy_group.sql` | platform-auth |
| 17 | `20260813_001_refactor_authz_policy_document_and_audit.sql` | platform-auth |
| 18 | `20260813_002_unify_authz_policy_groups.sql` | platform-auth |
| 19 | `20260814_001_platform_auth_add_discarded_policy_version_status.sql` | platform-auth |
| 20 | `20260814_002_repair_authz_policy_version_pointer.sql` | platform-auth |
| 21 | `20260814_003_repair_discarded_policy_pointer.sql` | platform-auth |
| 22 | `20260814_004_add_system_role_ownership.sql` | ruoyi-system |
| 23 | `20260816_001_add_grant_delete_permission.sql` | platform-auth |
| 24 | `20260817_001_add_auth_decision_log_menu.sql` | platform-auth |
| 25 | `20260820_001_create_authz_agent_token.sql` | platform-mcp |
| 26 | `20260821_001_extend_agent_token_lifecycle.sql` | platform-mcp |
| 27 | `20260821_002_align_platform_audit_time.sql` | platform-auth |
| 28 | `20260821_003_create_govdata_metadata.sql` | govdata-metadata |
| 29 | `20260821_004_create_govdata_metadata_harvest.sql` | govdata-metadata-harvest |
| 30 | `20260821_005_add_metadata_menus.sql` | govdata-metadata / govdata-metadata-harvest |
| 31 | `20260822_001_refactor_metadata_namespace_hierarchy.sql` | govdata-metadata |
| 32 | `20260823_001_align_metadata_history_menus.sql` | govdata-metadata |
| 33 | `20260823_002_migrate_metadata_to_table_revisions.sql` | govdata-metadata |
| 34 | `20260823_003_migrate_harvest_to_single_datasource.sql` | govdata-metadata-harvest |
| 35 | `20260826_001_complete_metadata_governance_and_impact.sql` | govdata-metadata / govdata-metadata-harvest |
| 36 | `20260906_001_create_query_access_log.sql` | govdata-query |
| 37 | `20261028_001_scope_harvest_runs_to_plan.sql` | govdata-metadata-harvest |
| 38 | `20261028_002_merge_metadata_history_into_catalog.sql` | govdata-metadata |
| 39 | `20261029_001_hide_metadata_harvest_menu.sql` | govdata-metadata-harvest |
| 40 | `20261029_002_allow_multiple_harvest_plans.sql` | govdata-metadata-harvest |
| 41 | `20261029_003_allow_harvest_regex_rules.sql` | govdata-metadata-harvest |

若环境并非从本项目最早版本逐步升级，先通过表结构、菜单和执行记录确认实际前置，再制定专项迁移方案；不要盲目补跑带有历史回填或清理语义的脚本。

-- module: platform-auth
-- depends-on: 20260811_002_add_authz_resource_ownership.sql
-- purpose: 为策略模板批量创建增加只用于管理和审计的策略组编码
-- expected-rows: 0（仅变更表结构，历史策略保持未分组）
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: 备份 authz_policy 并确认不存在同名列和索引
-- post-check: 检查 policy_group_code 和 idx_authz_policy_group 已创建
-- rollback-or-forward-fix: 保留可空分组列并使用前向修复；应用回退后可继续忽略该列

ALTER TABLE authz_policy
    ADD COLUMN policy_group_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT NULL COMMENT '模板批量创建的策略组编码' AFTER policy_name,
    ADD KEY idx_authz_policy_group (tenant_id, policy_group_code, status);

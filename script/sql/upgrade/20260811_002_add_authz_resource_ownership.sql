-- module: platform-auth
-- depends-on: 20260811_001_complete_authz_policy_lifecycle.sql
-- purpose: 补充资源所有人和权属部门路径属性，支持六种部门数据范围的 ABAC 条件
-- expected-rows: 0（仅变更表结构；历史资源由授权投影对账补齐）
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: 备份 authz_resource 并确认各领域授权投影对账入口可用
-- post-check: 检查 owner_user_id、owner_dept_path_json 和 idx_authz_resource_owner_user 已创建
-- rollback-or-forward-fix: 保留新增列并使用前向修复；回退应用前不得删除已被策略引用的资源属性

-- 授权资源补充部门层级和业务所有人属性，用于六种部门数据范围的 ABAC 条件。

ALTER TABLE authz_resource
    ADD COLUMN owner_user_id bigint DEFAULT NULL COMMENT '业务资源所有人ID' AFTER owner_dept_id,
    ADD COLUMN owner_dept_path_json json DEFAULT NULL COMMENT '权属部门祖先及自身ID路径JSON' AFTER owner_user_id,
    ADD KEY idx_authz_resource_owner_user (tenant_id, owner_user_id, status);

-- 历史资源需要通过各领域授权投影对账入口重放，补齐 owner_user_id 与 owner_dept_path_json。

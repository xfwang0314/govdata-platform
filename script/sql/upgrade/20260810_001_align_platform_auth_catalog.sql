-- module: platform-auth
-- depends-on: 20260803_004_add_platform_auth_menus.sql
-- purpose: 约束 Resource 安全分类编码与平台授权目录保持一致
-- expected-rows: 0（仅结构变更）
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 查询 classification_code 非空且不在 PUBLIC/INTERNAL/SENSITIVE/RESTRICTED 中的记录，结果必须为0
-- post-check: information_schema.table_constraints 中存在 ck_authz_resource_classification
-- rollback-or-forward-fix: ALTER TABLE authz_resource DROP CHECK ck_authz_resource_classification;

SELECT resource_id, classification_code
FROM authz_resource
WHERE classification_code IS NOT NULL
  AND classification_code NOT IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED');

ALTER TABLE authz_resource
    ADD CONSTRAINT ck_authz_resource_classification
        CHECK (classification_code IS NULL
            OR classification_code IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED'));

SELECT constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name = 'authz_resource'
  AND constraint_name = 'ck_authz_resource_classification';

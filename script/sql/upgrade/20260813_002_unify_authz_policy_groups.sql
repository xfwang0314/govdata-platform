-- 将自定义策略与模板策略统一纳入策略组生命周期。
-- 执行前必须备份 platform 控制库；仅支持 MySQL 8.0。

ALTER TABLE authz_policy_group
    ADD COLUMN source_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '创建来源：CUSTOM/TEMPLATE' AFTER policy_group_name,
    MODIFY COLUMN template_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '策略模板稳定编码，自定义策略为空',
    MODIFY COLUMN template_command_json json NULL
        COMMENT '模板配置快照JSON，自定义策略为空';

UPDATE authz_policy_group
SET source_type = 'TEMPLATE'
WHERE source_type IS NULL;

INSERT INTO authz_policy_group
(
    policy_group_id, tenant_id, policy_group_code, policy_group_name, source_type,
    template_code, template_command_json, lock_version, del_flag,
    create_dept, create_by, create_time, update_by, update_time
)
SELECT
    policy.policy_id,
    policy.tenant_id,
    CONCAT('pgrp_legacy_', policy.policy_id),
    policy.policy_name,
    'CUSTOM',
    NULL,
    NULL,
    0,
    '0',
    policy.create_dept,
    policy.create_by,
    policy.create_time,
    policy.update_by,
    policy.update_time
FROM authz_policy policy
WHERE policy.policy_group_code IS NULL
  AND policy.del_flag = '0';

UPDATE authz_policy policy
SET policy.policy_group_code = CONCAT('pgrp_legacy_', policy.policy_id)
WHERE policy.policy_group_code IS NULL
  AND policy.del_flag = '0';

ALTER TABLE authz_policy_group
    MODIFY COLUMN source_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '创建来源：CUSTOM/TEMPLATE',
    ADD KEY idx_authz_policy_group_source (tenant_id, source_type, update_time),
    ADD CONSTRAINT ck_authz_policy_group_source CHECK (source_type IN ('CUSTOM', 'TEMPLATE'));

ALTER TABLE authz_policy
    MODIFY COLUMN policy_group_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '策略所属稳定策略组编码';

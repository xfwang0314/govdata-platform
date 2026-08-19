-- module: platform-auth
-- depends-on: 20260810_002_add_auth_decision_query_permission.sql
-- purpose: 补齐策略草稿指针、版本乐观锁、子资源继承列与生命周期操作权限
-- expected-rows: 草稿策略数量（回填草稿指针）及缺失菜单数量
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: 备份 authz_policy、authz_policy_version、authz_policy_resource 并确认没有正在发布的策略
-- post-check: 检查新增列、索引和 19203-19205 菜单权限存在
-- rollback-or-forward-fix: 保留新增列；删除新增菜单，应用回退后再评估删除列

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy'
          AND column_name = 'draft_version_id'
    ),
    'SELECT ''authz_policy.draft_version_id already exists'' AS migration_status',
    'ALTER TABLE authz_policy ADD COLUMN draft_version_id bigint DEFAULT NULL COMMENT ''当前待编辑策略版本主键'' AFTER current_version_id'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

UPDATE authz_policy AS policy
JOIN (
    SELECT tenant_id, policy_id, policy_version_id
    FROM (
        SELECT tenant_id,
               policy_id,
               policy_version_id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, policy_id
                   ORDER BY version_no DESC, policy_version_id DESC
               ) AS row_no
        FROM authz_policy_version
        WHERE version_status IN ('DRAFT', 'REVIEWING')
    ) AS ranked_version
    WHERE row_no = 1
) AS draft
  ON draft.tenant_id = policy.tenant_id
 AND draft.policy_id = policy.policy_id
SET policy.draft_version_id = draft.policy_version_id
WHERE policy.draft_version_id IS NULL
  AND policy.status IN ('DRAFT', 'REVIEWING');

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_version'
          AND column_name = 'lock_version'
    ),
    'SELECT ''authz_policy_version.lock_version already exists'' AS migration_status',
    'ALTER TABLE authz_policy_version ADD COLUMN lock_version int NOT NULL DEFAULT 0 COMMENT ''草稿并发编辑乐观锁版本'' AFTER published_time'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_version'
          AND column_name = 'policy_name_snapshot'
    ),
    'SELECT ''authz_policy_version.policy_name_snapshot already exists'' AS migration_status',
    'ALTER TABLE authz_policy_version ADD COLUMN policy_name_snapshot varchar(200) DEFAULT NULL COMMENT ''该版本策略名称快照'' AFTER version_no'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_version'
          AND column_name = 'risk_level'
    ),
    'SELECT ''authz_policy_version.risk_level already exists'' AS migration_status',
    'ALTER TABLE authz_policy_version ADD COLUMN risk_level varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT ''该版本风险等级：LOW/MEDIUM/HIGH/CRITICAL'' AFTER policy_name_snapshot'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

UPDATE authz_policy_version AS version
JOIN authz_policy AS policy
  ON policy.tenant_id = version.tenant_id
 AND policy.policy_id = version.policy_id
SET version.policy_name_snapshot = COALESCE(version.policy_name_snapshot, policy.policy_name),
    version.risk_level = COALESCE(version.risk_level, policy.risk_level)
WHERE version.policy_name_snapshot IS NULL
   OR version.risk_level IS NULL;

ALTER TABLE authz_policy_version
    MODIFY COLUMN policy_name_snapshot varchar(200) NOT NULL COMMENT '该版本策略名称快照',
    MODIFY COLUMN risk_level varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '该版本风险等级：LOW/MEDIUM/HIGH/CRITICAL';

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_version'
          AND constraint_name = 'ck_authz_policy_ver_risk'
    ),
    'SELECT ''ck_authz_policy_ver_risk already exists'' AS migration_status',
    'ALTER TABLE authz_policy_version ADD CONSTRAINT ck_authz_policy_ver_risk CHECK (risk_level IN (''LOW'', ''MEDIUM'', ''HIGH'', ''CRITICAL''))'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_resource'
          AND column_name = 'include_children'
    ),
    'SELECT ''authz_policy_resource.include_children already exists'' AS migration_status',
    'ALTER TABLE authz_policy_resource ADD COLUMN include_children tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否包含后代资源'' AFTER resource_type, ADD CONSTRAINT ck_authz_policy_resource_children CHECK (include_children IN (0, 1))'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_resource'
          AND constraint_name = 'ck_authz_policy_resource_children'
    ),
    'SELECT ''ck_authz_policy_resource_children already exists'' AS migration_status',
    'ALTER TABLE authz_policy_resource ADD CONSTRAINT ck_authz_policy_resource_children CHECK (include_children IN (0, 1))'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_version'
          AND index_name = 'uk_authz_policy_ver_hash'
    ),
    'ALTER TABLE authz_policy_version DROP INDEX uk_authz_policy_ver_hash',
    'SELECT ''uk_authz_policy_ver_hash already absent'' AS migration_status'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'authz_policy_version'
          AND index_name = 'idx_authz_policy_ver_hash'
    ),
    'SELECT ''idx_authz_policy_ver_hash already exists'' AS migration_status',
    'ALTER TABLE authz_policy_version ADD KEY idx_authz_policy_ver_hash (tenant_id, policy_id, content_hash)'
);
PREPARE authz_stmt FROM @ddl;
EXECUTE authz_stmt;
DEALLOCATE PREPARE authz_stmt;

INSERT INTO sys_menu
SELECT 19203, '策略编辑', 19200, 3, '', '', '', 1, 0, 'F', '0', '0',
       'platform:auth:policy:edit', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19203);

INSERT INTO sys_menu
SELECT 19204, '策略新版本', 19200, 4, '', '', '', 1, 0, 'F', '0', '0',
       'platform:auth:policy:version', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19204);

INSERT INTO sys_menu
SELECT 19205, '策略停用', 19200, 5, '', '', '', 1, 0, 'F', '0', '0',
       'platform:auth:policy:disable', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19205);

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'authz_policy' AND column_name = 'draft_version_id')
    OR (table_name = 'authz_policy_version'
        AND column_name IN ('lock_version', 'policy_name_snapshot', 'risk_level'))
    OR (table_name = 'authz_policy_resource' AND column_name = 'include_children'))
ORDER BY table_name, column_name;

SELECT menu_id, menu_name, perms
FROM sys_menu
WHERE menu_id BETWEEN 19203 AND 19205
ORDER BY menu_id;

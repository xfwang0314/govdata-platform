-- module: platform-mcp
-- depends-on: 20260820_001_create_authz_agent_token.sql
-- purpose: 补充 Agent 凭据编辑、启停和 Token 轮换能力
-- expected-rows: authz_agent_token 新增 2 列，sys_menu 新增 3 行
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 20260820_001已执行，authz_agent_token存在
-- post-check: 生命周期字段和编辑、启停、轮换权限存在
-- rollback-or-forward-fix: 字段投入使用后只允许前向修复

SET @dbname = DATABASE();
SET @tablename = 'authz_agent_token';

SET @columnname = 'last_rotated_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) = 0,
  'ALTER TABLE `authz_agent_token` ADD COLUMN `last_rotated_time` datetime(3) DEFAULT NULL COMMENT ''最后轮换时间'' AFTER `last_used_time`',
  'SELECT 1'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'rotation_count';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) = 0,
  'ALTER TABLE `authz_agent_token` ADD COLUMN `rotation_count` int NOT NULL DEFAULT 0 COMMENT ''累计轮换次数'' AFTER `last_rotated_time`',
  'SELECT 1'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @constraintname = 'ck_authz_token_rotation_count';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = @dbname AND TABLE_NAME = @tablename
     AND CONSTRAINT_NAME = @constraintname) = 0,
  'ALTER TABLE `authz_agent_token` ADD CONSTRAINT `ck_authz_token_rotation_count` CHECK (`rotation_count` >= 0)',
  'SELECT 1'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
  (19605, '凭据编辑', 19600, 5, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19606, '凭据轮换', 19600, 6, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:rotate', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19607, '凭据启停', 19600, 7, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:status', '#', 103, 1, NOW(), NULL, NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  perms = VALUES(perms),
  update_time = NOW();

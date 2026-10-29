-- module: platform-mcp
-- depends-on: 20260817_001_add_auth_decision_log_menu.sql
-- purpose: 创建 Agent 访问凭据表 (authz_agent_token) 并注册统一管理菜单与权限
-- expected-rows: 1 table created, sys_menu 新增 8 行
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: authz_agent_token不存在，数据治理一级菜单存在
-- post-check: authz_agent_token存在且Agent管理菜单权限完整
-- rollback-or-forward-fix: 未产生凭据时可删除新表与菜单；产生数据后只允许前向修复

CREATE TABLE IF NOT EXISTS `authz_agent_token` (
  `token_id` bigint NOT NULL COMMENT '凭证雪花ID主键',
  `agent_name` varchar(64) NOT NULL COMMENT 'Agent名称(如: 张三的Cursor数据助手)',
  `agent_code` varchar(64) NOT NULL COMMENT 'Agent稳定业务编码',
  `token_hash` varchar(128) NOT NULL COMMENT 'Token哈希摘要(SHA-256防明文泄露)',
  `token_prefix` varchar(32) NOT NULL COMMENT 'Token安全前缀(脱敏展示, 如 mcp_sk_live_...)',
  `user_id` bigint NOT NULL COMMENT '归属用户ID',
  `dept_id` bigint NOT NULL COMMENT '归属部门ID快照',
  `tenant_id` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
  `allowed_categories` varchar(255) NOT NULL DEFAULT 'DATASOURCE' COMMENT '允许访问的MCP工具分类(英文逗号分隔, 如: DATASOURCE,STANDARD)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED/REVOKED)',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '过期时间(NULL表示长期有效)',
  `max_daily_requests` int NOT NULL DEFAULT 2000 COMMENT '每日最大请求配额',
  `used_requests_today` int NOT NULL DEFAULT 0 COMMENT '今日已用请求数',
  `last_used_time` datetime(3) DEFAULT NULL COMMENT '最后活跃时间',
  `last_rotated_time` datetime(3) DEFAULT NULL COMMENT '最后轮换时间',
  `rotation_count` int NOT NULL DEFAULT 0 COMMENT '累计轮换次数',
  `remark` varchar(500) DEFAULT NULL COMMENT '用途备注',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0存在 1删除)',
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_tenant_user_status` (`tenant_id`, `user_id`, `status`),
  KEY `idx_agent_code` (`agent_code`),
  CONSTRAINT `ck_authz_token_status` CHECK (`status` IN ('ACTIVE', 'DISABLED', 'REVOKED')),
  CONSTRAINT `ck_authz_token_rotation_count` CHECK (`rotation_count` >= 0),
  CONSTRAINT `ck_authz_token_del_flag` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent访问凭据表';

-- 清理由早期原型错误引入的明文凭据列；明文 Token 只在创建响应中返回一次。
SET @dbname = DATABASE();
SET @tablename = 'authz_agent_token';
SET @columnname = 'token_value';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'ALTER TABLE `authz_agent_token` DROP COLUMN `token_value`',
  'SELECT 1'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 注册「Agent 凭据管理」统一菜单与按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
  (19600, 'Agent凭据', 19000, 7, 'agent', 'platform/auth/agent/index', '', 1, 0, 'C', '0', '0', '', 'robot', 103, 1, NOW(), NULL, NULL, 'Agent访问凭据管理'),
  (19601, '凭据查询', 19600, 1, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:query', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19602, '凭据新增', 19600, 2, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:add', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19603, '凭据吊销', 19600, 3, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:revoke', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19604, '凭据删除', 19600, 4, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:delete', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19605, '凭据编辑', 19600, 5, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19606, '凭据轮换', 19600, 6, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:rotate', '#', 103, 1, NOW(), NULL, NULL, ''),
  (19607, '凭据启停', 19600, 7, '', '', '', 1, 0, 'F', '0', '0', 'platform:mcp:agent:status', '#', 103, 1, NOW(), NULL, NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  path = VALUES(path),
  component = VALUES(component),
  perms = VALUES(perms);

DELETE FROM sys_menu WHERE menu_id IN (19700, 19701, 19702, 19703);

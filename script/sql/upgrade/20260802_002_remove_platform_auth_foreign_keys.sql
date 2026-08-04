-- ============================================================================
-- 模块：platform-auth
-- 前置版本：已执行含物理外键的第一版 authz_* 建表脚本
-- 影响：仅删除 platform-auth 物理外键，不删除字段、索引或业务数据
-- 锁表风险：每个 ALTER TABLE 会获取元数据锁，生产执行前需确认无长事务
-- 回滚：不建议恢复物理外键；通过应用服务、事务、索引、对账与测试保证完整性
-- 验证：脚本末尾 foreign_key_count 应为 0
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS drop_platform_auth_fk_if_exists$$
CREATE PROCEDURE drop_platform_auth_fk_if_exists(
    IN p_table_name varchar(64),
    IN p_constraint_name varchar(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = p_table_name
          AND constraint_name = p_constraint_name
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        SET @drop_fk_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` DROP FOREIGN KEY `', REPLACE(p_constraint_name, '`', '``'), '`'
        );
        PREPARE drop_fk_statement FROM @drop_fk_sql;
        EXECUTE drop_fk_statement;
        DEALLOCATE PREPARE drop_fk_statement;
    END IF;
END$$

DELIMITER ;

CALL drop_platform_auth_fk_if_exists('authz_resource', 'fk_authz_resource_parent');
CALL drop_platform_auth_fk_if_exists('authz_resource_relation', 'fk_authz_relation_source');
CALL drop_platform_auth_fk_if_exists('authz_resource_relation', 'fk_authz_relation_target');
CALL drop_platform_auth_fk_if_exists('authz_policy_version', 'fk_authz_policy_ver_policy');
CALL drop_platform_auth_fk_if_exists('authz_policy_subject', 'fk_authz_policy_subject_ver');
CALL drop_platform_auth_fk_if_exists('authz_policy_resource', 'fk_authz_policy_resource_ver');
CALL drop_platform_auth_fk_if_exists('authz_policy_resource', 'fk_authz_policy_resource_id');
CALL drop_platform_auth_fk_if_exists('authz_policy_action', 'fk_authz_policy_action_ver');
CALL drop_platform_auth_fk_if_exists('authz_grant_scope', 'fk_authz_grant_scope_grant');
CALL drop_platform_auth_fk_if_exists('authz_grant_scope', 'fk_authz_grant_scope_resource');
CALL drop_platform_auth_fk_if_exists('authz_policy', 'fk_authz_policy_current_ver');

DROP PROCEDURE IF EXISTS drop_platform_auth_fk_if_exists;

SELECT COUNT(*) AS foreign_key_count
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND LEFT(table_name, 6) = 'authz_'
  AND constraint_type = 'FOREIGN KEY';

-- ============================================================================
-- 影响：移除已部署环境中的 SnailJob 菜单授权和菜单记录
-- 前置：停止并下线 SnailJob Server；备份控制库
-- 回滚：如确需恢复，从变更前备份恢复 menu_id=120 及其角色关联
-- ============================================================================

DELETE FROM sys_role_menu WHERE menu_id = 120;
DELETE FROM sys_menu WHERE menu_id = 120;

SELECT COUNT(*) AS snailjob_menu_count
FROM sys_menu
WHERE menu_id = 120 OR perms = 'monitor:snailjob:list';

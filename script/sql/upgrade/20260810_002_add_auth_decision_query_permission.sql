-- module: platform-auth
-- depends-on: 20260803_004_add_platform_auth_menus.sql
-- purpose: 新增授权决策日志查询权限
-- expected-rows: sys_menu 新增 1 行
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: SELECT menu_id, perms FROM sys_menu WHERE menu_id IN (19400, 19401);
-- post-check: SELECT menu_id, perms FROM sys_menu WHERE menu_id = 19401;
-- rollback-or-forward-fix: 未授权前可删除 menu_id=19401；已授权后使用前向修复

INSERT INTO sys_menu VALUES
    (19401, '决策日志查询', 19400, 1, '', '', '', 1, 0, 'F', '0', '0',
     'platform:auth:decision:query', '#', 103, 1, NOW(), NULL, NULL, '授权决策日志查询');

SELECT menu_id, menu_name, perms
FROM sys_menu
WHERE menu_id = 19401;

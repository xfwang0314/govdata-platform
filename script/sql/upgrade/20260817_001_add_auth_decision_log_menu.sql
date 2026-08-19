-- module: platform-auth
-- depends-on: 20260810_002_add_auth_decision_query_permission.sql
-- purpose: 新增授权决策审计日志菜单页面 (sys_menu 19450)
-- expected-rows: sys_menu 新增 1 行或更新
-- lock-risk: LOW
-- downtime-required: NO

-- 注册授权决策日志页面菜单 (sys_menu 是系统全局菜单表，不含 tenant_id 列)
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (19450, '决策日志', 19000, 5, 'log', 'platform/auth/log/index', '', 1, 0, 'C', '0', '0', 'platform:auth:decision:query', 'form', 103, 1, NOW(), NULL, NULL, '授权决策审计日志')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    perms = VALUES(perms);

-- 验证菜单记录
SELECT menu_id, menu_name, path, component, perms
FROM sys_menu
WHERE menu_id IN (19400, 19450);

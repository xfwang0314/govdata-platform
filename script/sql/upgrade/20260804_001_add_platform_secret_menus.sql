-- module: platform-secret
-- depends-on: sys_menu, platform-secret backend endpoints, platform-secret frontend page
-- purpose: add credential management menu and operation permissions
-- expected-rows: insert 5 sys_menu rows when none of the menu ids exist
-- lock-risk: low; point inserts by primary key
-- downtime-required: no
-- pre-check: confirm menu_id 19500 through 19504 are unused
-- post-check: query menu_id 19500 through 19504 and verify component and permissions
-- rollback-or-forward-fix: remove role-menu bindings first, then delete menu_id 19501 through 19504 and 19500

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 19500, '凭据管理', 19000, 5, 'secret', 'platform/secret/index', '', 1, 0,
       'C', '0', '0', 'platform:secret:list', 'key', 103, 1, NOW(), NULL, NULL, '平台敏感凭据统一管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19500);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 19501, '凭据查询', 19500, 1, '', '', '', 1, 0,
       'F', '0', '0', 'platform:secret:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19501);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 19502, '凭据新增', 19500, 2, '', '', '', 1, 0,
       'F', '0', '0', 'platform:secret:add', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19502);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 19503, '凭据轮换', 19500, 3, '', '', '', 1, 0,
       'F', '0', '0', 'platform:secret:rotate', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19503);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 19504, '凭据启停', 19500, 4, '', '', '', 1, 0,
       'F', '0', '0', 'platform:secret:edit', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 19504);

SELECT menu_id, menu_name, parent_id, component, perms
FROM sys_menu
WHERE menu_id BETWEEN 19500 AND 19504
ORDER BY menu_id;

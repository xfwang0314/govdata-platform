-- 共享授权删除按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, tenant_id, create_by, create_time, update_by, update_time, remark)
VALUES (19304, '授权删除', 19300, 4, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:grant:delete', '#', 103, 1, NOW(), NULL, NULL, '');

-- 共享授权修改草稿按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, tenant_id, create_by, create_time, update_by, update_time, remark)
VALUES (19305, '授权修改', 19300, 5, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:grant:edit', '#', 103, 1, NOW(), NULL, NULL, '');


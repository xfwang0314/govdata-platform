-- ============================================================================
-- 模块：platform-auth
-- 影响：新增平台授权前端联调菜单与功能权限
-- 前置：platform-auth 后端接口和前端对应页面存在
-- 回滚：先删除相关 sys_role_menu，再按子到父删除下列 menu_id
-- ============================================================================

INSERT INTO sys_menu VALUES
    (19000, '平台治理', 0, 6, 'platform', NULL, '', 1, 0, 'M', '0', '0', '', 'lock', 103, 1, NOW(), NULL, NULL, '平台通用治理目录'),
    (19100, '授权资源', 19000, 1, 'resource', 'platform/auth/resource/index', '', 1, 0, 'C', '0', '0', 'platform:auth:resource:query', 'tree-table', 103, 1, NOW(), NULL, NULL, '统一授权资源管理'),
    (19200, 'ABAC策略', 19000, 2, 'policy', 'platform/auth/policy/index', '', 1, 0, 'C', '0', '0', 'platform:auth:policy:query', 'shield', 103, 1, NOW(), NULL, NULL, 'ABAC策略管理'),
    (19300, '共享授权', 19000, 3, 'grant', 'platform/auth/grant/index', '', 1, 0, 'C', '0', '0', 'platform:auth:grant:query', 'peoples', 103, 1, NOW(), NULL, NULL, '运行时Grant管理'),
    (19400, '授权验证', 19000, 4, 'decision', 'platform/auth/decision/index', '', 1, 0, 'C', '0', '0', 'platform:auth:decision:evaluate', 'eye-open', 103, 1, NOW(), NULL, NULL, '授权决策联调页面'),
    (19101, '资源新增', 19100, 1, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:resource:add', '#', 103, 1, NOW(), NULL, NULL, ''),
    (19201, '策略新增', 19200, 1, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:policy:add', '#', 103, 1, NOW(), NULL, NULL, ''),
    (19202, '策略发布', 19200, 2, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:policy:publish', '#', 103, 1, NOW(), NULL, NULL, ''),
    (19301, '授权新增', 19300, 1, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:grant:add', '#', 103, 1, NOW(), NULL, NULL, ''),
    (19302, '授权激活', 19300, 2, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:grant:activate', '#', 103, 1, NOW(), NULL, NULL, ''),
    (19303, '授权撤销', 19300, 3, '', '', '', 1, 0, 'F', '0', '0', 'platform:auth:grant:revoke', '#', 103, 1, NOW(), NULL, NULL, '');

SELECT menu_id, menu_name, perms
FROM sys_menu
WHERE menu_id BETWEEN 19000 AND 19400
ORDER BY menu_id;

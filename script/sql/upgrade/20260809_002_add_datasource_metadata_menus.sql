-- ============================================================================
-- 模块：govdata-datasource
-- 前置：已执行 20260803_003_extend_datasource_management.sql
-- 影响：新增元数据探测（数据库/Schema、表清单、字段结构）按钮权限点
-- 锁表风险：无（仅向 sys_menu 表 INSERT）
-- 回滚：DELETE FROM sys_menu WHERE menu_id BETWEEN 20107 AND 20109;
-- ============================================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_dept, create_time, update_by, update_time, remark)
VALUES
    (20107, '数据库列表', 20100, 7, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:metadata:databases', '#', 103, 1, NOW(), NULL, NULL, '查询数据源下的数据库/Schema清单'),
    (20108, '表清单查询', 20100, 8, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:metadata:tables', '#', 103, 1, NOW(), NULL, NULL, '分页查询指定数据库/Schema下的表清单'),
    (20109, '字段结构探测', 20100, 9, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:metadata:columns', '#', 103, 1, NOW(), NULL, NULL, '批量查询指定表的字段结构元数据');

SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 20107 AND 20109;

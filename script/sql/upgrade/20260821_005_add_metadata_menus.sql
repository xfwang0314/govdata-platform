-- module: govdata-metadata / govdata-metadata-harvest
-- depends-on: 20260821_004_create_govdata_metadata_harvest.sql
-- purpose: 注册元数据目录、快照审核、采集计划和运行菜单权限
-- expected-rows: sys_menu新增13行
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 数据治理一级菜单20000存在且目标menu_id未占用
-- post-check: menu_id 20200至20501范围内本脚本定义的13行存在
-- rollback-or-forward-fix: 未授权使用时可按文件说明倒序删除；使用后只允许前向修复
-- ============================================================================
-- 模块：govdata-metadata / govdata-metadata-harvest
-- 前置：已部署数据治理一级菜单 20000，前端已发布对应页面
-- 影响：新增元数据目录、版本审核、采集计划和运行记录菜单权限
-- 锁表风险：仅写入少量菜单行
-- 回滚：按 menu_id 20501、20500、20405..20400、20302..20300、20201、20200 倒序删除
-- ============================================================================

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
    (20200, '元数据目录', 20000, 2, 'metadata', 'govdata/metadata/index', '', 1, 0, 'C', '0', '0', 'govdata:metadata:list', 'tree-table', 103, 1, NOW(), NULL, NULL, '版本化元数据资产目录'),
    (20300, '元数据版本', 20000, 3, 'metadata-snapshot', 'govdata/metadata/snapshot/index', '', 1, 0, 'C', '0', '0', 'govdata:metadata:snapshot:list', 'history', 103, 1, NOW(), NULL, NULL, '元数据快照与破坏性变更审核'),
    (20400, '采集计划', 20000, 4, 'metadata-harvest', 'govdata/metadata-harvest/index', '', 1, 0, 'C', '0', '0', 'govdata:metadata:harvest:list', 'time', 103, 1, NOW(), NULL, NULL, '元数据采集计划与规则'),
    (20500, '采集运行', 20000, 5, 'metadata-harvest-run', 'govdata/metadata-harvest/run/index', '', 1, 0, 'C', '0', '0', 'govdata:metadata:harvest:run:list', 'list', 103, 1, NOW(), NULL, NULL, '元数据采集运行记录'),
    (20201, '资产详情', 20200, 1, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:query', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20301, '版本详情', 20300, 1, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:snapshot:query', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20302, '版本审核', 20300, 2, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:snapshot:review', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20401, '计划详情', 20400, 1, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:harvest:query', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20402, '计划新增', 20400, 2, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:harvest:add', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20403, '计划修改', 20400, 3, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:harvest:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20404, '计划删除', 20400, 4, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:harvest:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20405, '执行采集', 20400, 5, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:harvest:execute', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20501, '运行详情', 20500, 1, '', '', '', 1, 0, 'F', '0', '0', 'govdata:metadata:harvest:run:query', '#', 103, 1, NOW(), NULL, NULL, '');

SELECT COUNT(*) AS metadata_menu_count FROM sys_menu WHERE menu_id BETWEEN 20200 AND 20501;

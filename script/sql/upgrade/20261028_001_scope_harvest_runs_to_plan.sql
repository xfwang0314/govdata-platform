-- module: govdata-metadata-harvest
-- depends-on: 20260823_001_align_metadata_history_menus.sql
-- purpose: 独立采集运行菜单改为采集计划下的权限，保留菜单ID与角色授权
-- expected-rows: 0-2
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 检查20500、20501菜单及角色绑定；20500应为旧采集运行菜单
-- post-check: 20500为20400下F类型权限且无component；20501归属20400；角色绑定不变
-- rollback-or-forward-fix: 新增前向修复脚本；配套前后端同时发布

UPDATE sys_menu
SET menu_name = '运行记录',
    parent_id = 20400,
    order_num = 6,
    path = '',
    component = '',
    menu_type = 'F',
    remark = '采集计划内运行记录权限'
WHERE menu_id = 20500
    AND perms = 'govdata:metadata:harvest:run:list';

UPDATE sys_menu
SET parent_id = 20400
WHERE menu_id = 20501
    AND perms = 'govdata:metadata:harvest:run:query';

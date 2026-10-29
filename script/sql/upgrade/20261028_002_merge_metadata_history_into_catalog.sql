-- module: govdata-metadata
-- depends-on: 20261028_001_scope_harvest_runs_to_plan.sql
-- purpose: 修订与变更入口并入元数据目录，保留权限ID及角色绑定
-- expected-rows: 0-4
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 检查20200、20300至20303菜单与角色绑定，20300应为旧历史菜单
-- post-check: 20300为20200下F类型权限且component为空；20301至20303归属20200；角色绑定不变
-- rollback-or-forward-fix: 新增前向修复脚本；前后端配套发布

UPDATE sys_menu
SET menu_name = '查看版本',
    parent_id = 20200,
    order_num = 6,
    path = '',
    component = '',
    menu_type = 'F',
    remark = '元数据目录内查看版本权限'
WHERE menu_id = 20300
    AND perms = 'govdata:metadata:revision:list';

UPDATE sys_menu
SET parent_id = 20200
WHERE menu_id IN (20301, 20302, 20303)
    AND parent_id = 20300
    AND perms IN ('govdata:metadata:revision:query', 'govdata:metadata:change-issue:list',
        'govdata:metadata:change-issue:manage');

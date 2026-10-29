-- module: govdata-metadata
-- depends-on: 20260822_001_refactor_metadata_namespace_hierarchy.sql
-- purpose: 将整库快照审核菜单切换为表级自动修订与变更问题权限
-- expected-rows: 3-4 sys_menu rows
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 20300 AND 20303;
-- post-check: SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 20300 AND 20303 ORDER BY menu_id;
-- rollback-or-forward-fix: 菜单元数据可通过后续前向脚本恢复，不影响业务表数据

UPDATE sys_menu
SET menu_name = '修订与变更',
    path = 'metadata-history',
    component = 'govdata/metadata/snapshot/index',
    perms = 'govdata:metadata:revision:list',
    remark = '表级自动修订与破坏性变更问题',
    update_time = NOW(3)
WHERE menu_id = 20300;

UPDATE sys_menu
SET menu_name = '修订详情',
    perms = 'govdata:metadata:revision:query',
    update_time = NOW(3)
WHERE menu_id = 20301;

UPDATE sys_menu
SET menu_name = '变更问题查询',
    perms = 'govdata:metadata:change-issue:list',
    update_time = NOW(3)
WHERE menu_id = 20302;

INSERT INTO sys_menu (
    menu_id,
    menu_name,
    parent_id,
    order_num,
    path,
    component,
    query_param,
    is_frame,
    is_cache,
    menu_type,
    visible,
    status,
    perms,
    icon,
    create_dept,
    create_by,
    create_time,
    update_by,
    update_time,
    remark
) SELECT
    20303,
    '变更问题处置',
    20300,
    3,
    '',
    '',
    '',
    1,
    0,
    'F',
    '0',
    '0',
    'govdata:metadata:change-issue:manage',
    '#',
    103,
    1,
    NOW(3),
    NULL,
    NULL,
    ''
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE menu_id = 20303
);

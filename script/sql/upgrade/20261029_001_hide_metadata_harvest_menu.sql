-- module: govdata-metadata-harvest
-- depends-on: 20261028_002_merge_metadata_history_into_catalog.sql
-- purpose: 隐藏元数据采集侧边栏菜单，保留数据源管理可直达的工作台路由和权限节点
-- expected-rows: sys_menu更新1行
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: menu_id 20400存在，组件为govdata/metadata-harvest/index
-- post-check: menu_id 20400的visible为1，路由组件与权限编码保持不变
-- rollback-or-forward-fix: 若需恢复独立菜单入口，使用前向脚本将visible恢复为0
-- ============================================================================
-- 元数据采集不再作为一级业务入口展示。用户从数据源管理的“采集管理”进入独立工作台，
-- 因此仅隐藏菜单，不删除路由记录、角色授权或其下功能权限节点。
-- ============================================================================

UPDATE sys_menu
SET visible = '1',
    remark = '数据源管理进入的元数据采集隐藏工作台',
    update_time = NOW()
WHERE menu_id = 20400
  AND parent_id = 20000
  AND menu_type = 'C'
  AND component = 'govdata/metadata-harvest/index';

SELECT menu_id,
       menu_name,
       visible,
       perms,
       component
FROM sys_menu
WHERE menu_id = 20400;

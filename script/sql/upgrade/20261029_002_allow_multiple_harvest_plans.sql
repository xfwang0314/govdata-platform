-- module: govdata-metadata-harvest
-- depends-on: 20261029_001_hide_metadata_harvest_menu.sql
-- purpose: 允许一个数据源配置多个采集计划，同时保留运行级数据源并发互斥
-- expected-rows: gd_metadata_harvest_plan删除1个唯一索引和1个兼容字段
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: gd_metadata_harvest_plan存在uk_gd_metadata_harvest_plan_datasource索引和active_datasource_key字段
-- post-check: 计划表不再按数据源唯一，运行表仍保留uk_gd_metadata_harvest_active_source唯一索引
-- rollback-or-forward-fix: 已创建多计划后不可直接恢复唯一约束；仅允许先合并重复计划再通过前向脚本恢复
-- ============================================================================
-- 采集计划的业务边界调整为“每个计划绑定一个数据源，一个数据源可拥有多个计划”。
-- gd_metadata_harvest_run.active_datasource_key继续保证同一数据源不会并发执行多个采集运行。
-- ============================================================================

ALTER TABLE gd_metadata_harvest_plan
    DROP INDEX uk_gd_metadata_harvest_plan_datasource,
    DROP COLUMN active_datasource_key;

SELECT table_name,
       index_name,
       non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('gd_metadata_harvest_plan', 'gd_metadata_harvest_run')
  AND index_name IN ('uk_gd_metadata_harvest_plan_datasource', 'uk_gd_metadata_harvest_active_source')
ORDER BY table_name,
         index_name;

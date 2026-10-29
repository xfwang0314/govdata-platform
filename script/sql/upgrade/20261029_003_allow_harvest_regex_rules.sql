-- module: govdata-metadata-harvest
-- depends-on: 20261029_002_allow_multiple_harvest_plans.sql
-- purpose: 允许采集范围规则使用经后端校验的正则匹配方式
-- expected-rows: 不修改数据，仅替换1个CHECK约束
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: gd_metadata_harvest_rule存在ck_gd_metadata_harvest_match_type约束
-- post-check: CHECK约束包含REGEX；旧匹配方式保持可用
-- rollback-or-forward-fix: 先将REGEX规则迁移为旧方式或删除，再恢复原CHECK约束

ALTER TABLE gd_metadata_harvest_rule
    DROP CHECK ck_gd_metadata_harvest_match_type,
    ADD CONSTRAINT ck_gd_metadata_harvest_match_type
        CHECK (match_type IN ('EXACT','PREFIX','SUFFIX','CONTAINS','REGEX'));

SELECT constraint_name,
       check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name = 'ck_gd_metadata_harvest_match_type';

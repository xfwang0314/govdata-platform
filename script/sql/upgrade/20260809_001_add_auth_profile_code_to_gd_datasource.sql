-- ============================================================================
-- 模块：govdata-datasource
-- 前置：已执行 20260803_001_create_govdata_datasource.sql
-- 影响：gd_datasource 新增 auth_profile_code 字段，且允许 secret_ref 为空（支持 NONE 认证）
-- 锁表风险：ALTER TABLE 修改表结构
-- 回滚：ALTER TABLE gd_datasource DROP COLUMN auth_profile_code;
-- ============================================================================

ALTER TABLE gd_datasource
    ADD COLUMN auth_profile_code varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Connector认证方式编码' AFTER connector_version,
    MODIFY COLUMN secret_ref varchar(200) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '凭据引用；NONE认证为空，禁止保存凭据原文';

SELECT column_name, is_nullable, column_type 
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
  AND table_name = 'gd_datasource' 
  AND column_name IN ('auth_profile_code', 'secret_ref');

-- module: govdata-metadata
-- depends-on: 20260821_005_add_metadata_menus.sql
-- purpose: 将数据库投影统一为Catalog，并固化数据源/Catalog/Schema/表/列层级所需的授权父链
-- expected-rows: 元数据资产与快照存量行数
-- lock-risk: MEDIUM
-- downtime-required: YES
-- pre-check: gd_metadata_database存在；gd_datasource.authz_resource_id无空值；元数据类型只包含既有约束允许值
-- post-check: DATABASE类型为0；快照datasource_resource_id为空为0；Catalog明细行数与迁移前数据库明细行数一致
-- rollback-or-forward-fix: 变更包含表重命名与存量类型更新，失败后停止应用并以前向修复脚本处理

SET NAMES utf8mb4;

SELECT COUNT(*) AS datasource_resource_missing_count
FROM gd_metadata_snapshot snapshot
INNER JOIN gd_datasource datasource
        ON datasource.tenant_id = snapshot.tenant_id
       AND datasource.datasource_id = snapshot.datasource_id
WHERE datasource.authz_resource_id IS NULL;

ALTER TABLE gd_metadata_database RENAME TO gd_metadata_catalog;
ALTER TABLE gd_metadata_catalog
    RENAME INDEX uk_gd_metadata_database_asset TO uk_gd_metadata_catalog_asset;

ALTER TABLE gd_metadata_asset
    DROP CHECK ck_gd_metadata_asset_type;

UPDATE gd_metadata_asset
SET asset_type = 'CATALOG'
WHERE asset_type = 'DATABASE';

ALTER TABLE gd_metadata_asset
    ADD CONSTRAINT ck_gd_metadata_asset_type
        CHECK (asset_type IN ('CATALOG', 'SCHEMA', 'TABLE', 'COLUMN', 'FILE'));

ALTER TABLE gd_metadata_snapshot_asset
    DROP CHECK ck_gd_metadata_snapshot_asset_type;

UPDATE gd_metadata_snapshot_asset
SET asset_type = 'CATALOG'
WHERE asset_type = 'DATABASE';

ALTER TABLE gd_metadata_snapshot_asset
    ADD CONSTRAINT ck_gd_metadata_snapshot_asset_type
        CHECK (asset_type IN ('CATALOG', 'SCHEMA', 'TABLE', 'COLUMN', 'FILE'));

ALTER TABLE gd_metadata_snapshot
    ADD COLUMN datasource_resource_id bigint DEFAULT NULL COMMENT '数据源统一授权资源主键快照'
        AFTER datasource_id;

UPDATE gd_metadata_snapshot snapshot
INNER JOIN gd_datasource datasource
        ON datasource.tenant_id = snapshot.tenant_id
       AND datasource.datasource_id = snapshot.datasource_id
SET snapshot.datasource_resource_id = datasource.authz_resource_id
WHERE snapshot.datasource_resource_id IS NULL;

ALTER TABLE gd_metadata_snapshot
    MODIFY COLUMN datasource_resource_id bigint NOT NULL COMMENT '数据源统一授权资源主键快照';

SELECT COUNT(*) AS legacy_database_asset_count
FROM gd_metadata_asset
WHERE asset_type = 'DATABASE';

SELECT COUNT(*) AS legacy_database_snapshot_asset_count
FROM gd_metadata_snapshot_asset
WHERE asset_type = 'DATABASE';

SELECT COUNT(*) AS snapshot_resource_missing_count
FROM gd_metadata_snapshot
WHERE datasource_resource_id IS NULL;

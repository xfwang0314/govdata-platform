-- module: govdata-metadata
-- depends-on: 20260823_001_align_metadata_history_menus.sql
-- purpose: 将已部署的整库快照结构兼容迁移为当前状态和表级修订
-- expected-rows: 现有元数据资产行数
-- lock-risk: HIGH
-- downtime-required: YES
-- pre-check: FILE资产为0；目标新表不存在
-- post-check: 新增列无NULL；每张TABLE资产存在current_revision_no
-- rollback-or-forward-fix: 原快照表保留不删；失败后停止应用并以前向脚本修复

SET NAMES utf8mb4;

SELECT COUNT(*) AS unsupported_file_asset_count
FROM gd_metadata_asset
WHERE asset_type = 'FILE'
  AND del_flag = '0';

ALTER TABLE gd_metadata_asset
    ADD COLUMN connector_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最近观察Connector编码' AFTER datasource_id,
    ADD COLUMN connector_version varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最近观察Connector版本' AFTER connector_code,
    ADD COLUMN canonical_key varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Connector规范化定位键' AFTER connector_version,
    ADD COLUMN native_object_id varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '源端可靠原生对象标识' AFTER canonical_key,
    ADD COLUMN original_name varchar(200) DEFAULT NULL COMMENT '源端原始技术名称' AFTER native_object_id,
    ADD COLUMN last_harvest_run_id bigint DEFAULT NULL COMMENT '最近确认运行主键' AFTER last_seen_time,
    ADD COLUMN content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '技术内容SHA-256' AFTER structure_hash,
    ADD COLUMN current_revision_no int DEFAULT NULL COMMENT 'Table当前自动修订号' AFTER content_hash;

UPDATE gd_metadata_asset asset
LEFT JOIN gd_metadata_snapshot snapshot
       ON snapshot.tenant_id = asset.tenant_id
      AND snapshot.snapshot_id = asset.current_snapshot_id
SET asset.connector_code = COALESCE(snapshot.connector_code, 'legacy'),
    asset.connector_version = COALESCE(snapshot.connector_version, 'unknown'),
    asset.canonical_key = asset.external_key,
    asset.original_name = asset.technical_name,
    asset.last_harvest_run_id = COALESCE(snapshot.harvest_run_id, 0),
    asset.content_hash = asset.structure_hash,
    asset.current_revision_no = CASE WHEN asset.asset_type = 'TABLE' THEN 1 ELSE NULL END
WHERE asset.asset_id > 0;

ALTER TABLE gd_metadata_asset
    MODIFY COLUMN external_key varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '旧版定位键，兼容迁移期间保留',
    MODIFY COLUMN technical_name varchar(200) DEFAULT NULL COMMENT '旧版技术名，兼容迁移期间保留',
    MODIFY COLUMN connector_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近观察Connector编码',
    MODIFY COLUMN connector_version varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近观察Connector版本',
    MODIFY COLUMN canonical_key varchar(500) COLLATE utf8mb4_bin NOT NULL COMMENT 'Connector规范化定位键',
    MODIFY COLUMN original_name varchar(200) NOT NULL COMMENT '源端原始技术名称',
    MODIFY COLUMN last_harvest_run_id bigint NOT NULL COMMENT '最近确认运行主键',
    MODIFY COLUMN content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技术内容SHA-256',
    DROP CHECK ck_gd_metadata_asset_type,
    DROP CHECK ck_gd_metadata_asset_technical_status,
    ADD CONSTRAINT ck_gd_metadata_asset_type CHECK (asset_type IN ('CATALOG','SCHEMA','TABLE','COLUMN')),
    ADD CONSTRAINT ck_gd_metadata_asset_technical_status CHECK (technical_status IN ('ACTIVE','MISSING','OUT_OF_SCOPE','DISABLED')),
    ADD UNIQUE KEY uk_gd_metadata_asset_key (tenant_id, datasource_id, asset_type, canonical_key),
    ADD UNIQUE KEY uk_gd_metadata_asset_native (tenant_id, datasource_id, asset_type, native_object_id),
    ADD KEY idx_gd_metadata_asset_source (tenant_id, datasource_id, technical_status, asset_id);

ALTER TABLE gd_metadata_column
    ADD COLUMN revision_no int DEFAULT NULL COMMENT '字段自身修订号' AFTER table_asset_id,
    ADD COLUMN technical_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '技术状态' AFTER comment_text,
    ADD COLUMN structure_hash char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '执行结构SHA-256' AFTER technical_status,
    ADD COLUMN content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '技术内容SHA-256' AFTER structure_hash,
    ADD COLUMN first_seen_time datetime(3) DEFAULT NULL COMMENT '首次发现时间' AFTER content_hash,
    ADD COLUMN last_seen_time datetime(3) DEFAULT NULL COMMENT '最近发现时间' AFTER first_seen_time,
    ADD COLUMN last_harvest_run_id bigint DEFAULT NULL COMMENT '最近确认运行主键' AFTER last_seen_time;

UPDATE gd_metadata_column column_detail
INNER JOIN gd_metadata_asset asset
        ON asset.tenant_id = column_detail.tenant_id
       AND asset.asset_id = column_detail.asset_id
SET column_detail.revision_no = 1,
    column_detail.technical_status = asset.technical_status,
    column_detail.structure_hash = asset.structure_hash,
    column_detail.content_hash = asset.content_hash,
    column_detail.first_seen_time = asset.first_seen_time,
    column_detail.last_seen_time = asset.last_seen_time,
    column_detail.last_harvest_run_id = asset.last_harvest_run_id
WHERE column_detail.asset_id > 0;

ALTER TABLE gd_metadata_column
    MODIFY COLUMN revision_no int NOT NULL COMMENT '字段自身修订号',
    MODIFY COLUMN technical_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技术状态',
    MODIFY COLUMN structure_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行结构SHA-256',
    MODIFY COLUMN content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技术内容SHA-256',
    MODIFY COLUMN first_seen_time datetime(3) NOT NULL COMMENT '首次发现时间',
    MODIFY COLUMN last_seen_time datetime(3) NOT NULL COMMENT '最近发现时间',
    MODIFY COLUMN last_harvest_run_id bigint NOT NULL COMMENT '最近确认运行主键';

CREATE TABLE gd_metadata_table_revision (
    table_revision_id bigint NOT NULL COMMENT '表修订主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    table_asset_id bigint NOT NULL COMMENT '稳定表资产主键',
    revision_no int NOT NULL COMMENT '表内修订号',
    previous_revision_no int DEFAULT NULL COMMENT '前一修订号',
    harvest_run_id bigint NOT NULL COMMENT '采集运行主键',
    change_level varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更等级',
    structure_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行结构SHA-256',
    content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技术内容SHA-256',
    change_summary_json json NOT NULL COMMENT '结构化差异摘要',
    observed_time datetime(3) NOT NULL COMMENT '观察生效时间',
    actor_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
    actor_id varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体ID',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (table_revision_id),
    UNIQUE KEY uk_gd_metadata_table_revision (tenant_id, table_asset_id, revision_no),
    UNIQUE KEY uk_gd_metadata_table_revision_run (tenant_id, table_asset_id, harvest_run_id),
    KEY idx_gd_metadata_table_revision_time (tenant_id, table_asset_id, observed_time, table_revision_id),
    CONSTRAINT ck_gd_metadata_table_revision_level CHECK (change_level IN ('DOCUMENTATION_ONLY','NON_BREAKING','BREAKING'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '表自动修订历史';

CREATE TABLE gd_metadata_table_revision_column (
    revision_column_id bigint NOT NULL COMMENT '修订字段快照主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    table_revision_id bigint NOT NULL COMMENT '表修订主键',
    column_asset_id bigint NOT NULL COMMENT '稳定字段资产主键',
    column_revision_no int NOT NULL COMMENT '字段修订号',
    original_name varchar(200) NOT NULL COMMENT '字段名快照',
    ordinal_position int NOT NULL COMMENT '顺序快照',
    data_type varchar(100) NOT NULL COMMENT '规范化类型快照',
    native_type varchar(200) NOT NULL COMMENT '源端类型快照',
    length_value int DEFAULT NULL COMMENT '长度快照',
    precision_value int DEFAULT NULL COMMENT '精度快照',
    scale_value int DEFAULT NULL COMMENT '小数位快照',
    nullable_flag tinyint(1) NOT NULL COMMENT '可空快照',
    primary_key_flag tinyint(1) NOT NULL COMMENT '主键快照',
    default_expression varchar(500) DEFAULT NULL COMMENT '默认表达式快照',
    comment_text varchar(500) DEFAULT NULL COMMENT '注释快照',
    structure_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段结构SHA-256',
    content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段内容SHA-256',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (revision_column_id),
    UNIQUE KEY uk_gd_metadata_revision_column (tenant_id, table_revision_id, column_asset_id),
    KEY idx_gd_metadata_revision_column_order (tenant_id, table_revision_id, ordinal_position, revision_column_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '表修订完整字段快照';

CREATE TABLE gd_metadata_change_issue (
    change_issue_id bigint NOT NULL COMMENT '变更问题主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    issue_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定问题编码',
    table_asset_id bigint NOT NULL COMMENT '表资产主键',
    from_revision_no int DEFAULT NULL COMMENT '变更前修订号',
    to_revision_no int NOT NULL COMMENT '变更后修订号',
    change_level varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更等级',
    issue_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '问题状态',
    change_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更指纹',
    active_dedupe_key varchar(500) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '活动问题去重键',
    change_summary_json json NOT NULL COMMENT '结构化差异摘要',
    detected_time datetime(3) NOT NULL COMMENT '发现时间',
    assignee_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '处理主体类型',
    assignee_id varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '处理主体ID',
    resolved_time datetime(3) DEFAULT NULL COMMENT '关闭时间',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路ID',
    create_dept bigint DEFAULT NULL COMMENT '创建部门ID',
    create_by bigint DEFAULT NULL COMMENT '创建人ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by bigint DEFAULT NULL COMMENT '更新人ID',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (change_issue_id),
    UNIQUE KEY uk_gd_metadata_change_issue_code (tenant_id, issue_code),
    UNIQUE KEY uk_gd_metadata_change_issue_active (tenant_id, active_dedupe_key),
    KEY idx_gd_metadata_change_issue_table (tenant_id, table_asset_id, issue_status, change_issue_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '高风险元数据结构变更问题';

INSERT INTO gd_metadata_table_revision (
    table_revision_id, tenant_id, table_asset_id, revision_no, previous_revision_no,
    harvest_run_id, change_level, structure_hash, content_hash, change_summary_json,
    observed_time, actor_type, actor_id, trace_id
)
SELECT asset_id, tenant_id, asset_id, 1, NULL,
       last_harvest_run_id, 'NON_BREAKING', structure_hash, content_hash,
       JSON_OBJECT('legacyMigration', TRUE), last_seen_time, 'SYSTEM', 'metadata-migration', NULL
FROM gd_metadata_asset
WHERE asset_type = 'TABLE'
  AND del_flag = '0';

INSERT INTO gd_metadata_table_revision_column (
    revision_column_id, tenant_id, table_revision_id, column_asset_id, column_revision_no,
    original_name, ordinal_position, data_type, native_type, length_value,
    precision_value, scale_value, nullable_flag, primary_key_flag, default_expression,
    comment_text, structure_hash, content_hash
)
SELECT column_asset.asset_id, column_asset.tenant_id, column_asset.parent_asset_id,
       column_asset.asset_id, column_detail.revision_no, column_asset.original_name,
       column_detail.ordinal_position, column_detail.data_type, column_detail.native_type,
       column_detail.length_value, column_detail.precision_value, column_detail.scale_value,
       column_detail.nullable_flag, column_detail.primary_key_flag, column_detail.default_expression,
       column_detail.comment_text, column_detail.structure_hash, column_detail.content_hash
FROM gd_metadata_asset column_asset
INNER JOIN gd_metadata_column column_detail
        ON column_detail.tenant_id = column_asset.tenant_id
       AND column_detail.asset_id = column_asset.asset_id
WHERE column_asset.asset_type = 'COLUMN'
  AND column_asset.del_flag = '0';

SELECT COUNT(*) AS metadata_table_without_revision
FROM gd_metadata_asset
WHERE asset_type = 'TABLE'
  AND current_revision_no IS NULL;

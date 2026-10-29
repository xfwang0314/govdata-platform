-- module: govdata-metadata
-- depends-on: 20260821_002_align_platform_audit_time.sql
-- purpose: 创建第一版版本化元数据目录表
-- expected-rows: 创建11张空表
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 对应gd_metadata表均不存在
-- post-check: 文件末尾information_schema计数为11
-- rollback-or-forward-fix: 未产生业务数据时可删除新表；产生数据后只允许前向修复
-- govdata-metadata 元数据目录建表（不可变前向升级）
-- 前置：对应 gd_metadata* 表均不存在，且 20260821_002 已执行。
-- 锁风险：CREATE TABLE 获取短时元数据锁；生产执行前确认无同名建表操作。
-- 回滚：上线前失败可删除本脚本已创建且确认无业务数据的表；上线后只允许前向修复。
-- 验证：执行文件末尾 information_schema 计数查询，并核对结果与 Baseline 一致。

-- ============================================================================
-- 政务数据中台 govdata-metadata P0 运行基线
-- 数据库：MySQL 8.0；模块拥有版本化元数据目录，不保存采集运行事实
-- ============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS gd_metadata_change;
DROP TABLE IF EXISTS gd_metadata_snapshot_asset;
DROP TABLE IF EXISTS gd_metadata_snapshot;
DROP TABLE IF EXISTS gd_metadata_dataset_asset;
DROP TABLE IF EXISTS gd_metadata_dataset;
DROP TABLE IF EXISTS gd_metadata_file;
DROP TABLE IF EXISTS gd_metadata_column;
DROP TABLE IF EXISTS gd_metadata_table;
DROP TABLE IF EXISTS gd_metadata_schema;
DROP TABLE IF EXISTS gd_metadata_database;
DROP TABLE IF EXISTS gd_metadata_asset;

CREATE TABLE gd_metadata_asset
(
    asset_id             bigint        NOT NULL COMMENT '元数据资产主键，雪花ID',
    tenant_id            varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    authz_resource_id    bigint        DEFAULT NULL COMMENT 'platform-auth资源投影主键',
    asset_code           varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定资产编码',
    asset_type           varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资产类型',
    parent_asset_id      bigint        DEFAULT NULL COMMENT '父资产主键',
    datasource_id        bigint        NOT NULL COMMENT '数据源主键，仅作跨模块稳定引用',
    current_snapshot_id  bigint        DEFAULT NULL COMMENT '当前已发布快照主键',
    external_key         varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '数据源内稳定物理标识',
    technical_name       varchar(200)  NOT NULL COMMENT '技术名称',
    display_name         varchar(200)  DEFAULT NULL COMMENT '展示名称',
    business_name        varchar(200)  DEFAULT NULL COMMENT '人工治理业务名称',
    description          varchar(500)  DEFAULT NULL COMMENT '人工治理说明',
    owner_dept_id        bigint        NOT NULL COMMENT '权属部门ID',
    steward_user_id      bigint        DEFAULT NULL COMMENT '数据管理员用户ID',
    classification_code varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INTERNAL' COMMENT '安全分类编码',
    security_level       int           NOT NULL DEFAULT 1 COMMENT '安全等级1-5',
    technical_status     varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '技术状态',
    governance_status    varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNMANAGED' COMMENT '治理状态',
    first_seen_time      datetime(3)   NOT NULL COMMENT '首次发现时间',
    last_seen_time       datetime(3)   NOT NULL COMMENT '最近发现时间',
    structure_hash       char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256结构哈希',
    lock_version         int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag             char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept          bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by            bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by            bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_asset_code (tenant_id, asset_code),
    UNIQUE KEY uk_gd_metadata_asset_external (tenant_id, datasource_id, asset_type, external_key),
    UNIQUE KEY uk_gd_metadata_asset_authz (tenant_id, authz_resource_id),
    KEY idx_gd_metadata_asset_parent (tenant_id, parent_asset_id, asset_type, asset_id),
    KEY idx_gd_metadata_asset_owner (tenant_id, owner_dept_id, governance_status, asset_id),
    KEY idx_gd_metadata_asset_current (tenant_id, current_snapshot_id, asset_id),
    CONSTRAINT ck_gd_metadata_asset_type CHECK (asset_type IN ('DATABASE', 'SCHEMA', 'TABLE', 'COLUMN', 'FILE')),
    CONSTRAINT ck_gd_metadata_asset_classification CHECK (classification_code IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED')),
    CONSTRAINT ck_gd_metadata_asset_security CHECK (security_level BETWEEN 1 AND 5),
    CONSTRAINT ck_gd_metadata_asset_technical_status CHECK (technical_status IN ('ACTIVE', 'MISSING', 'DISABLED')),
    CONSTRAINT ck_gd_metadata_asset_governance_status CHECK (governance_status IN ('UNMANAGED', 'MANAGED', 'ARCHIVED')),
    CONSTRAINT ck_gd_metadata_asset_del CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据资产当前已发布投影';

CREATE TABLE gd_metadata_database
(
    asset_id          bigint       NOT NULL COMMENT '数据库资产主键',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    catalog_name      varchar(200) NOT NULL COMMENT 'Catalog名称',
    database_product  varchar(100) DEFAULT NULL COMMENT '数据库产品',
    product_version   varchar(100) DEFAULT NULL COMMENT '产品版本',
    default_charset   varchar(64)  DEFAULT NULL COMMENT '默认字符集',
    default_collation varchar(100) DEFAULT NULL COMMENT '默认排序规则',
    properties_json   json         DEFAULT NULL COMMENT '受Schema约束的扩展技术属性',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_database_asset (tenant_id, asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据库资产技术明细';

CREATE TABLE gd_metadata_schema
(
    asset_id        bigint       NOT NULL COMMENT 'Schema资产主键',
    tenant_id       varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    schema_name     varchar(200) NOT NULL COMMENT 'Schema名称',
    schema_owner    varchar(200) DEFAULT NULL COMMENT '源端Schema Owner',
    properties_json json         DEFAULT NULL COMMENT '受Schema约束的扩展技术属性',
    create_time     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_schema_asset (tenant_id, asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Schema资产技术明细';

CREATE TABLE gd_metadata_table
(
    asset_id          bigint       NOT NULL COMMENT '表资产主键',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    table_type        varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '表类型',
    row_estimate      bigint       DEFAULT NULL COMMENT '估算行数',
    data_size_bytes   bigint       DEFAULT NULL COMMENT '估算数据字节数',
    engine_name       varchar(64)  DEFAULT NULL COMMENT '存储引擎',
    partitioned_flag  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否分区',
    comment_text      varchar(500) DEFAULT NULL COMMENT '源端表注释',
    properties_json   json         DEFAULT NULL COMMENT '受Schema约束的扩展技术属性',
    create_dept       bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by         bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_table_asset (tenant_id, asset_id),
    KEY idx_gd_metadata_table_type (tenant_id, table_type, asset_id),
    CONSTRAINT ck_gd_metadata_table_partitioned CHECK (partitioned_flag IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '表资产技术明细';

CREATE TABLE gd_metadata_column
(
    asset_id           bigint       NOT NULL COMMENT '字段资产主键',
    tenant_id          varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    table_asset_id     bigint       NOT NULL COMMENT '所属表资产主键',
    ordinal_position   int          NOT NULL COMMENT '字段顺序',
    data_type          varchar(100) NOT NULL COMMENT '规范化数据类型',
    native_type        varchar(200) NOT NULL COMMENT '源端数据类型',
    length_value       int          DEFAULT NULL COMMENT '长度',
    precision_value    int          DEFAULT NULL COMMENT '精度',
    scale_value        int          DEFAULT NULL COMMENT '小数位',
    nullable_flag      tinyint(1)   NOT NULL COMMENT '是否可空',
    primary_key_flag   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否主键',
    unique_flag        tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否唯一',
    auto_increment_flag tinyint(1)  NOT NULL DEFAULT 0 COMMENT '是否自增',
    default_expression varchar(500) DEFAULT NULL COMMENT '默认表达式，仅展示不执行',
    comment_text       varchar(500) DEFAULT NULL COMMENT '源端字段注释',
    create_dept        bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by          bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by          bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_column_asset (tenant_id, asset_id),
    KEY idx_gd_metadata_column_table (tenant_id, table_asset_id, ordinal_position, asset_id),
    CONSTRAINT ck_gd_metadata_column_ordinal CHECK (ordinal_position > 0),
    CONSTRAINT ck_gd_metadata_column_nullable CHECK (nullable_flag IN (0, 1)),
    CONSTRAINT ck_gd_metadata_column_pk CHECK (primary_key_flag IN (0, 1)),
    CONSTRAINT ck_gd_metadata_column_unique CHECK (unique_flag IN (0, 1)),
    CONSTRAINT ck_gd_metadata_column_auto CHECK (auto_increment_flag IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字段资产技术明细';

CREATE TABLE gd_metadata_file
(
    asset_id           bigint       NOT NULL COMMENT '文件资产主键',
    tenant_id          varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    storage_provider   varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '存储提供者编码',
    storage_object_ref varchar(500) NOT NULL COMMENT '受控存储对象引用',
    original_file_name varchar(500) NOT NULL COMMENT '原始文件名',
    content_type       varchar(100) DEFAULT NULL COMMENT '内容类型',
    file_format        varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '文件格式',
    file_size          bigint       NOT NULL COMMENT '文件字节数',
    checksum           char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256校验值',
    expire_time        datetime(3)  DEFAULT NULL COMMENT '失效时间',
    properties_json    json         DEFAULT NULL COMMENT '受Schema约束的扩展技术属性',
    create_time        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_file_asset (tenant_id, asset_id),
    CONSTRAINT ck_gd_metadata_file_size CHECK (file_size >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件资产技术明细';

CREATE TABLE gd_metadata_dataset
(
    dataset_id          bigint        NOT NULL COMMENT '数据集主键，雪花ID',
    tenant_id           varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    authz_resource_id   bigint        DEFAULT NULL COMMENT 'platform-auth资源投影主键',
    dataset_code        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定数据集编码',
    dataset_name        varchar(200)  NOT NULL COMMENT '数据集名称',
    description         varchar(500)  DEFAULT NULL COMMENT '数据集说明',
    owner_dept_id       bigint        NOT NULL COMMENT '权属部门ID',
    steward_user_id     bigint        DEFAULT NULL COMMENT '数据管理员用户ID',
    classification_code varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INTERNAL' COMMENT '安全分类编码',
    security_level      int           NOT NULL DEFAULT 1 COMMENT '安全等级1-5',
    dataset_status      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT '数据集状态',
    lock_version        int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag            char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept         bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (dataset_id),
    UNIQUE KEY uk_gd_metadata_dataset_code (tenant_id, dataset_code),
    UNIQUE KEY uk_gd_metadata_dataset_authz (tenant_id, authz_resource_id),
    KEY idx_gd_metadata_dataset_owner (tenant_id, owner_dept_id, dataset_status, dataset_id),
    CONSTRAINT ck_gd_metadata_dataset_classification CHECK (classification_code IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED')),
    CONSTRAINT ck_gd_metadata_dataset_security CHECK (security_level BETWEEN 1 AND 5),
    CONSTRAINT ck_gd_metadata_dataset_status CHECK (dataset_status IN ('DRAFT', 'ACTIVE', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_gd_metadata_dataset_del CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '治理数据集';

CREATE TABLE gd_metadata_dataset_asset
(
    binding_id     bigint       NOT NULL COMMENT '数据集资产绑定主键，雪花ID',
    tenant_id      varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    dataset_id     bigint       NOT NULL COMMENT '数据集主键',
    asset_id       bigint       NOT NULL COMMENT '资产主键',
    usage_role     varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PRIMARY' COMMENT '资产用途角色',
    display_order  int          NOT NULL DEFAULT 0 COMMENT '展示顺序',
    effective_time datetime(3)  NOT NULL COMMENT '生效时间',
    expire_time    datetime(3)  DEFAULT NULL COMMENT '失效时间',
    create_by      bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_gd_metadata_dataset_asset (tenant_id, dataset_id, asset_id, usage_role),
    KEY idx_gd_metadata_dataset_asset_reverse (tenant_id, asset_id, dataset_id),
    CONSTRAINT ck_gd_metadata_dataset_asset_order CHECK (display_order >= 0),
    CONSTRAINT ck_gd_metadata_dataset_asset_time CHECK (expire_time IS NULL OR expire_time > effective_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据集资产绑定';

CREATE TABLE gd_metadata_snapshot
(
    snapshot_id       bigint        NOT NULL COMMENT '目录快照主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    snapshot_code     varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定快照编码',
    datasource_id     bigint        NOT NULL COMMENT '数据源主键',
    owner_dept_id     bigint        NOT NULL COMMENT '快照资产归属部门ID',
    harvest_run_id    bigint        NOT NULL COMMENT '采集运行主键，仅作跨模块稳定引用',
    connector_code    varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '采集时连接器编码',
    connector_version varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '采集时连接器版本',
    snapshot_no       int           NOT NULL COMMENT '数据源内递增快照号',
    asset_count       int           NOT NULL DEFAULT 0 COMMENT '资产数量',
    content_hash      char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256内容哈希',
    snapshot_status   varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '快照状态',
    breaking_count    int           NOT NULL DEFAULT 0 COMMENT '破坏性变更数量',
    validated_time    datetime(3)   DEFAULT NULL COMMENT '校验完成时间',
    published_by      bigint        DEFAULT NULL COMMENT '发布人ID',
    published_time    datetime(3)   DEFAULT NULL COMMENT '发布时间',
    rejected_by       bigint        DEFAULT NULL COMMENT '拒绝人ID',
    rejected_time     datetime(3)   DEFAULT NULL COMMENT '拒绝时间',
    rejection_reason  varchar(500)  DEFAULT NULL COMMENT '拒绝原因',
    create_dept       bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by         bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by         bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time       datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_gd_metadata_snapshot_code (tenant_id, snapshot_code),
    UNIQUE KEY uk_gd_metadata_snapshot_run_source (tenant_id, harvest_run_id, datasource_id),
    UNIQUE KEY uk_gd_metadata_snapshot_no (tenant_id, datasource_id, snapshot_no),
    KEY idx_gd_metadata_snapshot_status (tenant_id, snapshot_status, create_time, snapshot_id),
    KEY idx_gd_metadata_snapshot_owner (tenant_id, owner_dept_id, create_time, snapshot_id),
    CONSTRAINT ck_gd_metadata_snapshot_no CHECK (snapshot_no > 0),
    CONSTRAINT ck_gd_metadata_snapshot_count CHECK (asset_count >= 0 AND breaking_count >= 0),
    CONSTRAINT ck_gd_metadata_snapshot_status CHECK (snapshot_status IN ('COLLECTED', 'VALIDATED', 'REVIEW_REQUIRED', 'PUBLISHED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可变元数据目录快照';

CREATE TABLE gd_metadata_snapshot_asset
(
    snapshot_asset_id         bigint       NOT NULL COMMENT '快照资产主键，雪花ID',
    tenant_id                 varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    snapshot_id               bigint       NOT NULL COMMENT '目录快照主键',
    asset_id                  bigint       NOT NULL COMMENT '稳定资产主键',
    external_key              varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '数据源内稳定物理标识',
    asset_type                varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资产类型',
    parent_external_key       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '父资产物理标识',
    structure_hash            char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256结构哈希',
    normalized_definition_json json        NOT NULL COMMENT '受Schema约束的规范化定义JSON，最大64KB',
    create_dept               bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by                 bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time               datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by                 bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time               datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (snapshot_asset_id),
    UNIQUE KEY uk_gd_metadata_snapshot_asset (tenant_id, snapshot_id, asset_type, external_key),
    KEY idx_gd_metadata_snapshot_asset_id (tenant_id, asset_id, snapshot_id),
    CONSTRAINT ck_gd_metadata_snapshot_asset_type CHECK (asset_type IN ('DATABASE', 'SCHEMA', 'TABLE', 'COLUMN', 'FILE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可变快照资产版本';

CREATE TABLE gd_metadata_change
(
    change_id           bigint        NOT NULL COMMENT '元数据变更主键，雪花ID',
    tenant_id           varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    datasource_id       bigint        NOT NULL COMMENT '数据源主键',
    asset_id            bigint        NOT NULL COMMENT '资产主键',
    previous_snapshot_id bigint       DEFAULT NULL COMMENT '前一已发布快照主键',
    current_snapshot_id bigint        NOT NULL COMMENT '候选快照主键',
    change_type         varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更类型',
    breaking_flag       tinyint(1)    NOT NULL COMMENT '是否破坏性变更',
    before_hash         char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '变更前结构哈希',
    after_hash          char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '变更后结构哈希',
    change_summary_json json          NOT NULL COMMENT '受Schema约束的变更摘要JSON',
    change_status       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
    reviewed_by         bigint        DEFAULT NULL COMMENT '审核人ID',
    reviewed_time       datetime(3)   DEFAULT NULL COMMENT '审核时间',
    review_comment      varchar(500)  DEFAULT NULL COMMENT '审核意见',
    trace_id            varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    detected_time       datetime(3)   NOT NULL COMMENT '检测时间',
    create_dept         bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (change_id),
    UNIQUE KEY uk_gd_metadata_change (tenant_id, current_snapshot_id, asset_id, change_type),
    KEY idx_gd_metadata_change_review (tenant_id, breaking_flag, change_status, detected_time, change_id),
    KEY idx_gd_metadata_change_asset (tenant_id, asset_id, detected_time, change_id),
    CONSTRAINT ck_gd_metadata_change_breaking CHECK (breaking_flag IN (0, 1)),
    CONSTRAINT ck_gd_metadata_change_status CHECK (change_status IN ('DETECTED', 'REVIEW_REQUIRED', 'APPROVED', 'REJECTED', 'APPLIED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据结构变更与审核记录';

SELECT COUNT(*) AS govdata_metadata_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'gd_metadata_asset', 'gd_metadata_database', 'gd_metadata_schema', 'gd_metadata_table',
    'gd_metadata_column', 'gd_metadata_file', 'gd_metadata_dataset', 'gd_metadata_dataset_asset',
    'gd_metadata_snapshot', 'gd_metadata_snapshot_asset', 'gd_metadata_change'
  );

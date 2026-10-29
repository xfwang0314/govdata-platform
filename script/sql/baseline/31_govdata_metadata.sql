-- govdata-metadata baseline (MySQL 8)
SET NAMES utf8mb4;

DROP TABLE IF EXISTS gd_metadata_change_issue;
DROP TABLE IF EXISTS gd_metadata_table_revision_column;
DROP TABLE IF EXISTS gd_metadata_table_revision;
DROP TABLE IF EXISTS gd_metadata_column;
DROP TABLE IF EXISTS gd_metadata_table;
DROP TABLE IF EXISTS gd_metadata_schema;
DROP TABLE IF EXISTS gd_metadata_catalog;
DROP TABLE IF EXISTS gd_metadata_asset;

-- ----------------------------
-- 1、元数据稳定资产与当前状态表
-- ----------------------------
CREATE TABLE gd_metadata_asset (
    asset_id bigint NOT NULL COMMENT '元数据资产主键，雪花ID',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    authz_resource_id bigint DEFAULT NULL COMMENT 'Catalog、Schema或Table授权资源投影主键',
    asset_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定资产编码',
    asset_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资产类型',
    parent_asset_id bigint DEFAULT NULL COMMENT '父元数据资产主键',
    datasource_id bigint NOT NULL COMMENT '数据源稳定引用',
    connector_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近观察Connector编码',
    connector_version varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近观察Connector版本',
    canonical_key varchar(500) COLLATE utf8mb4_bin NOT NULL COMMENT 'Connector规范化定位键',
    native_object_id varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '源端可靠原生对象标识',
    original_name varchar(200) NOT NULL COMMENT '源端原始技术名称',
    display_name varchar(200) DEFAULT NULL COMMENT '人工治理展示名称',
    business_name varchar(200) DEFAULT NULL COMMENT '人工治理业务名称',
    description varchar(500) DEFAULT NULL COMMENT '人工治理说明',
    owner_dept_id bigint NOT NULL COMMENT '权属部门ID',
    steward_user_id bigint DEFAULT NULL COMMENT '数据管理员用户ID',
    classification_code varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INTERNAL' COMMENT '安全分类',
    security_level int NOT NULL DEFAULT 1 COMMENT '安全等级1-5',
    technical_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '技术状态',
    governance_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNMANAGED' COMMENT '治理状态',
    first_seen_time datetime(3) NOT NULL COMMENT '首次发现时间',
    last_seen_time datetime(3) NOT NULL COMMENT '最近发现时间',
    last_harvest_run_id bigint NOT NULL COMMENT '最近确认运行主键',
    structure_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行结构SHA-256',
    content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技术内容SHA-256',
    current_revision_no int DEFAULT NULL COMMENT 'Table当前自动修订号',
    lock_version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept bigint DEFAULT NULL COMMENT '创建部门ID',
    create_by bigint DEFAULT NULL COMMENT '创建人ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by bigint DEFAULT NULL COMMENT '更新人ID',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_asset_code (tenant_id, asset_code),
    UNIQUE KEY uk_gd_metadata_asset_key (tenant_id, datasource_id, asset_type, canonical_key),
    UNIQUE KEY uk_gd_metadata_asset_native (tenant_id, datasource_id, asset_type, native_object_id),
    UNIQUE KEY uk_gd_metadata_asset_authz (tenant_id, authz_resource_id),
    KEY idx_gd_metadata_asset_parent (tenant_id, parent_asset_id, asset_type, asset_id),
    KEY idx_gd_metadata_asset_source (tenant_id, datasource_id, technical_status, asset_id),
    KEY idx_gd_metadata_asset_owner (tenant_id, owner_dept_id, governance_status, asset_id),
    CONSTRAINT ck_gd_metadata_asset_type CHECK (asset_type IN ('CATALOG','SCHEMA','TABLE','COLUMN')),
    CONSTRAINT ck_gd_metadata_asset_class CHECK (classification_code IN ('PUBLIC','INTERNAL','SENSITIVE','RESTRICTED')),
    CONSTRAINT ck_gd_metadata_asset_security CHECK (security_level BETWEEN 1 AND 5),
    CONSTRAINT ck_gd_metadata_asset_technical CHECK (technical_status IN ('ACTIVE','MISSING','OUT_OF_SCOPE','DISABLED')),
    CONSTRAINT ck_gd_metadata_asset_governance CHECK (governance_status IN ('UNMANAGED','MANAGED','ARCHIVED')),
    CONSTRAINT ck_gd_metadata_asset_revision CHECK (current_revision_no IS NULL OR current_revision_no > 0),
    CONSTRAINT ck_gd_metadata_asset_del CHECK (del_flag IN ('0','1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据稳定资产与当前状态';

-- ----------------------------
-- 2、Catalog当前技术明细表
-- ----------------------------
CREATE TABLE gd_metadata_catalog (
    asset_id bigint NOT NULL COMMENT 'Catalog资产主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    catalog_name varchar(200) NOT NULL COMMENT 'Catalog源端名称',
    database_product varchar(100) DEFAULT NULL COMMENT '数据库产品',
    product_version varchar(100) DEFAULT NULL COMMENT '产品版本',
    default_charset varchar(64) DEFAULT NULL COMMENT '默认字符集',
    default_collation varchar(100) DEFAULT NULL COMMENT '默认排序规则',
    properties_json json DEFAULT NULL COMMENT '扩展技术属性',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_catalog_asset (tenant_id, asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Catalog当前技术明细';

-- ----------------------------
-- 3、Schema当前技术明细表
-- ----------------------------
CREATE TABLE gd_metadata_schema (
    asset_id bigint NOT NULL COMMENT 'Schema资产主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    schema_name varchar(200) NOT NULL COMMENT 'Schema源端名称',
    schema_owner varchar(200) DEFAULT NULL COMMENT '源端Schema Owner',
    properties_json json DEFAULT NULL COMMENT '扩展技术属性',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_schema_asset (tenant_id, asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Schema当前技术明细';

-- ----------------------------
-- 4、表当前技术明细表
-- ----------------------------
CREATE TABLE gd_metadata_table (
    asset_id bigint NOT NULL COMMENT '表资产主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    table_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '表类型',
    row_estimate bigint DEFAULT NULL COMMENT '估算行数',
    data_size_bytes bigint DEFAULT NULL COMMENT '估算字节数',
    engine_name varchar(64) DEFAULT NULL COMMENT '存储引擎',
    partitioned_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否分区',
    comment_text varchar(500) DEFAULT NULL COMMENT '源端表注释',
    properties_json json DEFAULT NULL COMMENT '扩展技术属性',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_table_asset (tenant_id, asset_id),
    KEY idx_gd_metadata_table_type (tenant_id, table_type, asset_id),
    CONSTRAINT ck_gd_metadata_table_partitioned CHECK (partitioned_flag IN (0,1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '表当前技术明细';

-- ----------------------------
-- 5、字段当前技术状态表
-- ----------------------------
CREATE TABLE gd_metadata_column (
    asset_id bigint NOT NULL COMMENT '字段稳定资产主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    table_asset_id bigint NOT NULL COMMENT '所属表资产主键',
    revision_no int NOT NULL COMMENT '字段自身修订号',
    ordinal_position int NOT NULL COMMENT '字段顺序',
    data_type varchar(100) NOT NULL COMMENT '规范化类型',
    native_type varchar(200) NOT NULL COMMENT '源端类型',
    length_value int DEFAULT NULL COMMENT '长度',
    precision_value int DEFAULT NULL COMMENT '精度',
    scale_value int DEFAULT NULL COMMENT '小数位',
    nullable_flag tinyint(1) NOT NULL COMMENT '是否可空',
    primary_key_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主键',
    unique_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否唯一',
    auto_increment_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自增',
    default_expression varchar(500) DEFAULT NULL COMMENT '默认表达式',
    comment_text varchar(500) DEFAULT NULL COMMENT '源端字段注释',
    technical_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '技术状态',
    structure_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行结构SHA-256',
    content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '技术内容SHA-256',
    first_seen_time datetime(3) NOT NULL COMMENT '首次发现时间',
    last_seen_time datetime(3) NOT NULL COMMENT '最近发现时间',
    last_harvest_run_id bigint NOT NULL COMMENT '最近确认运行主键',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_gd_metadata_column_asset (tenant_id, asset_id),
    KEY idx_gd_metadata_column_table (tenant_id, table_asset_id, ordinal_position, asset_id),
    CONSTRAINT ck_gd_metadata_column_revision CHECK (revision_no > 0),
    CONSTRAINT ck_gd_metadata_column_ordinal CHECK (ordinal_position > 0),
    CONSTRAINT ck_gd_metadata_column_nullable CHECK (nullable_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_column_pk CHECK (primary_key_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_column_unique CHECK (unique_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_column_auto CHECK (auto_increment_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_column_technical CHECK (technical_status IN ('ACTIVE','MISSING','OUT_OF_SCOPE','DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字段当前技术状态';

-- ----------------------------
-- 6、表自动修订历史表
-- ----------------------------
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
    CONSTRAINT ck_gd_metadata_table_revision_no CHECK (revision_no > 0),
    CONSTRAINT ck_gd_metadata_table_previous_revision CHECK (previous_revision_no IS NULL OR previous_revision_no > 0),
    CONSTRAINT ck_gd_metadata_table_change_level CHECK (change_level IN ('DOCUMENTATION_ONLY','NON_BREAKING','BREAKING'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '表自动修订历史';

-- ----------------------------
-- 7、表修订完整字段快照表
-- ----------------------------
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
    KEY idx_gd_metadata_revision_column_order (tenant_id, table_revision_id, ordinal_position, revision_column_id),
    CONSTRAINT ck_gd_metadata_revision_column_no CHECK (column_revision_no > 0),
    CONSTRAINT ck_gd_metadata_revision_column_ordinal CHECK (ordinal_position > 0),
    CONSTRAINT ck_gd_metadata_revision_column_nullable CHECK (nullable_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_revision_column_pk CHECK (primary_key_flag IN (0,1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '表修订完整字段快照';

-- ----------------------------
-- 8、高风险元数据结构变更问题表
-- ----------------------------
CREATE TABLE gd_metadata_change_issue (
    change_issue_id bigint NOT NULL COMMENT '变更问题主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    issue_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定问题编码',
    issue_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '问题类型',
    table_asset_id bigint NOT NULL COMMENT '表资产主键',
    from_revision_no int DEFAULT NULL COMMENT '变更前修订号',
    to_revision_no int NOT NULL COMMENT '变更后修订号',
    change_level varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更等级',
    issue_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '问题状态',
    change_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更指纹',
    active_dedupe_key varchar(500) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '未关闭问题去重键',
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
    KEY idx_gd_metadata_change_issue_table (tenant_id, table_asset_id, issue_status, change_issue_id),
    CONSTRAINT ck_gd_metadata_change_issue_revision CHECK (to_revision_no > 0 AND (from_revision_no IS NULL OR from_revision_no > 0)),
    CONSTRAINT ck_gd_metadata_change_issue_level CHECK (change_level IN ('BREAKING')),
    CONSTRAINT ck_gd_metadata_change_issue_type CHECK (issue_type IN ('STRUCTURE_BREAKING','TABLE_MISSING')),
    CONSTRAINT ck_gd_metadata_change_issue_status CHECK (issue_status IN ('OPEN','ACKNOWLEDGED','REMEDIATING','RESOLVED','DISMISSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '高风险元数据结构变更问题';

-- 9. 下游影响投影（可由来源模块重建）
CREATE TABLE gd_metadata_impact_projection (
    impact_projection_id bigint NOT NULL COMMENT '影响投影主键，雪花ID',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    source_module varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '依赖事实来源模块',
    consumer_type varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下游消费者类型',
    consumer_id varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下游消费者稳定ID',
    consumer_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '下游消费者稳定编码快照',
    consumer_name varchar(200) NOT NULL COMMENT '下游消费者名称快照',
    consumer_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下游消费者状态快照',
    asset_id bigint NOT NULL COMMENT '直接依赖的表或字段资产ID',
    table_asset_id bigint NOT NULL COMMENT '影响归集所属表资产ID',
    dependency_type varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源模块定义的依赖类型',
    source_updated_time datetime(3) DEFAULT NULL COMMENT '来源事实更新时间',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (impact_projection_id),
    UNIQUE KEY uk_gd_metadata_impact_consumer_asset (tenant_id, source_module, consumer_type, consumer_id, asset_id, dependency_type),
    KEY idx_gd_metadata_impact_table (tenant_id, table_asset_id, source_module, consumer_type),
    KEY idx_gd_metadata_impact_asset (tenant_id, asset_id, source_module, consumer_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据资产下游依赖可重建投影';

SELECT COUNT(*) AS govdata_metadata_table_count FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('gd_metadata_asset','gd_metadata_catalog','gd_metadata_schema','gd_metadata_table','gd_metadata_column','gd_metadata_table_revision','gd_metadata_table_revision_column','gd_metadata_change_issue','gd_metadata_impact_projection');

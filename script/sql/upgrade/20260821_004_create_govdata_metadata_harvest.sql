-- module: govdata-metadata-harvest
-- depends-on: 20260821_003_create_govdata_metadata.sql
-- purpose: 创建第一版元数据采集计划与运行表
-- expected-rows: 创建6张空表
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 对应gd_metadata_harvest表均不存在
-- post-check: 文件末尾information_schema计数为6
-- rollback-or-forward-fix: 未产生业务数据时可删除新表；产生数据后只允许前向修复
-- govdata-metadata-harvest 元数据采集建表（不可变前向升级）
-- 前置：对应 gd_metadata* 表均不存在，且 20260821_002 已执行。
-- 锁风险：CREATE TABLE 获取短时元数据锁；生产执行前确认无同名建表操作。
-- 回滚：上线前失败可删除本脚本已创建且确认无业务数据的表；上线后只允许前向修复。
-- 验证：执行文件末尾 information_schema 计数查询，并核对结果与 Baseline 一致。

-- ============================================================================
-- 政务数据中台 govdata-metadata-harvest P0 运行基线（MySQL 8）
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS gd_metadata_harvest_raw_result;
DROP TABLE IF EXISTS gd_metadata_harvest_run_source;
DROP TABLE IF EXISTS gd_metadata_harvest_run;
DROP TABLE IF EXISTS gd_metadata_harvest_rule;
DROP TABLE IF EXISTS gd_metadata_harvest_plan_source;
DROP TABLE IF EXISTS gd_metadata_harvest_plan;

CREATE TABLE gd_metadata_harvest_plan
(
    harvest_plan_id   bigint        NOT NULL COMMENT '采集计划主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    harvest_plan_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定采集计划编码',
    authz_resource_id bigint        DEFAULT NULL COMMENT '统一授权资源主键，异步投影后回填',
    harvest_plan_name varchar(200)  NOT NULL COMMENT '采集计划名称',
    schedule_type     varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL' COMMENT '调度类型',
    cron_expression   varchar(100)  DEFAULT NULL COMMENT 'Quartz Cron表达式',
    owner_dept_id     bigint        NOT NULL COMMENT '权属部门ID',
    plan_status       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DISABLED' COMMENT '计划状态',
    lock_version      int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag          char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept       bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by         bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by         bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time       datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (harvest_plan_id),
    UNIQUE KEY uk_gd_metadata_harvest_plan_code (tenant_id, harvest_plan_code),
    KEY idx_gd_metadata_harvest_plan_owner (tenant_id, owner_dept_id, plan_status, harvest_plan_id),
    KEY idx_gd_metadata_harvest_plan_resource (tenant_id, authz_resource_id),
    CONSTRAINT ck_gd_metadata_harvest_plan_schedule CHECK (schedule_type IN ('MANUAL', 'CRON')),
    CONSTRAINT ck_gd_metadata_harvest_plan_status CHECK (plan_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_gd_metadata_harvest_plan_cron CHECK ((schedule_type = 'MANUAL' AND cron_expression IS NULL) OR (schedule_type = 'CRON' AND cron_expression IS NOT NULL)),
    CONSTRAINT ck_gd_metadata_harvest_plan_del CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据采集计划';

CREATE TABLE gd_metadata_harvest_plan_source
(
    binding_id       bigint       NOT NULL COMMENT '计划数据源绑定主键，雪花ID',
    tenant_id        varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    harvest_plan_id  bigint       NOT NULL COMMENT '采集计划主键',
    datasource_id    bigint       NOT NULL COMMENT '数据源主键，仅作跨模块稳定引用',
    scope_json       json         DEFAULT NULL COMMENT '受Schema约束的采集范围JSON，禁止任意SQL',
    sort_no          int          NOT NULL DEFAULT 0 COMMENT '执行顺序',
    binding_status   varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '绑定状态',
    create_dept      bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by        bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by        bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_gd_metadata_harvest_plan_source (tenant_id, harvest_plan_id, datasource_id),
    KEY idx_gd_metadata_harvest_source_reverse (tenant_id, datasource_id, binding_status, binding_id),
    CONSTRAINT ck_gd_metadata_harvest_source_order CHECK (sort_no >= 0),
    CONSTRAINT ck_gd_metadata_harvest_source_status CHECK (binding_status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集计划数据源绑定';

CREATE TABLE gd_metadata_harvest_rule
(
    harvest_rule_id bigint       NOT NULL COMMENT '采集规则主键，雪花ID',
    tenant_id       varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    binding_id      bigint       NOT NULL COMMENT '计划数据源绑定主键',
    rule_scope      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则作用域',
    rule_type       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则类型',
    match_type      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配类型',
    rule_pattern    varchar(500) NOT NULL COMMENT '单个匹配值，不允许多值拼接',
    sort_no         int          NOT NULL DEFAULT 0 COMMENT '规则顺序',
    rule_status     varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '规则状态',
    create_dept     bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by       bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by       bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (harvest_rule_id),
    UNIQUE KEY uk_gd_metadata_harvest_rule (tenant_id, binding_id, rule_scope, rule_type, match_type, rule_pattern),
    KEY idx_gd_metadata_harvest_rule_order (tenant_id, binding_id, sort_no, harvest_rule_id),
    CONSTRAINT ck_gd_metadata_harvest_rule_scope CHECK (rule_scope IN ('CATALOG', 'SCHEMA', 'TABLE')),
    CONSTRAINT ck_gd_metadata_harvest_rule_type CHECK (rule_type IN ('INCLUDE', 'EXCLUDE')),
    CONSTRAINT ck_gd_metadata_harvest_match_type CHECK (match_type IN ('EXACT', 'PREFIX', 'SUFFIX', 'CONTAINS')),
    CONSTRAINT ck_gd_metadata_harvest_rule_status CHECK (rule_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_gd_metadata_harvest_rule_order_check CHECK (sort_no >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据采集过滤规则';

CREATE TABLE gd_metadata_harvest_run
(
    harvest_run_id   bigint        NOT NULL COMMENT '采集运行主键，雪花ID',
    tenant_id        varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    harvest_run_no   varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定采集运行编号',
    harvest_plan_id  bigint        NOT NULL COMMENT '采集计划主键',
    owner_dept_id    bigint        NOT NULL COMMENT '运行时计划权属部门快照',
    trigger_type     varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '触发类型',
    trigger_ref      varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '触发来源引用',
    actor_type       varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行主体类型',
    actor_id         varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行主体稳定ID',
    run_status       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行状态',
    active_run_key   varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '运行中计划互斥键，结束后置空',
    source_count     int           NOT NULL DEFAULT 0 COMMENT '数据源数量',
    success_count    int           NOT NULL DEFAULT 0 COMMENT '成功数据源数量',
    failure_count    int           NOT NULL DEFAULT 0 COMMENT '失败数据源数量',
    started_time     datetime(3)   NOT NULL COMMENT '开始时间',
    completed_time   datetime(3)   DEFAULT NULL COMMENT '完成时间',
    trace_id         varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    create_dept      bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by        bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by        bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (harvest_run_id),
    UNIQUE KEY uk_gd_metadata_harvest_run_no (tenant_id, harvest_run_no),
    UNIQUE KEY uk_gd_metadata_harvest_trigger (tenant_id, harvest_plan_id, trigger_type, trigger_ref),
    UNIQUE KEY uk_gd_metadata_harvest_active_run (tenant_id, active_run_key),
    KEY idx_gd_metadata_harvest_run_plan (tenant_id, harvest_plan_id, started_time, harvest_run_id),
    KEY idx_gd_metadata_harvest_run_owner (tenant_id, owner_dept_id, started_time, harvest_run_id),
    KEY idx_gd_metadata_harvest_run_status (tenant_id, run_status, started_time, harvest_run_id),
    CONSTRAINT ck_gd_metadata_harvest_run_trigger CHECK (trigger_type IN ('MANUAL', 'CRON', 'RETRY')),
    CONSTRAINT ck_gd_metadata_harvest_run_status CHECK (run_status IN ('QUEUED', 'RUNNING', 'PARTIAL_SUCCESS', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_gd_metadata_harvest_run_count CHECK (source_count >= 0 AND success_count >= 0 AND failure_count >= 0),
    CONSTRAINT ck_gd_metadata_harvest_run_time CHECK (completed_time IS NULL OR completed_time >= started_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据采集运行';

CREATE TABLE gd_metadata_harvest_run_source
(
    run_source_id          bigint        NOT NULL COMMENT '运行数据源分项主键，雪花ID',
    tenant_id              varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    harvest_run_id         bigint        NOT NULL COMMENT '采集运行主键',
    datasource_id          bigint        NOT NULL COMMENT '数据源主键',
    connector_code         varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Connector编码快照',
    connector_version      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Connector版本快照',
    run_status             varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分项状态',
    attempt_no             int           NOT NULL DEFAULT 1 COMMENT '尝试次数',
    metadata_snapshot_id   bigint        DEFAULT NULL COMMENT '目录模块返回的快照主键',
    collected_table_count  int           NOT NULL DEFAULT 0 COMMENT '采集表数量',
    failure_code           varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定失败编码',
    failure_summary        varchar(1000) DEFAULT NULL COMMENT '脱敏失败摘要',
    started_time           datetime(3)   NOT NULL COMMENT '开始时间',
    completed_time         datetime(3)   DEFAULT NULL COMMENT '完成时间',
    trace_id               varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    create_dept            bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by              bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time            datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by              bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time            datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (run_source_id),
    UNIQUE KEY uk_gd_metadata_harvest_run_source (tenant_id, harvest_run_id, datasource_id),
    KEY idx_gd_metadata_harvest_run_source_retry (tenant_id, datasource_id, run_status, started_time, run_source_id),
    CONSTRAINT ck_gd_metadata_harvest_run_source_status CHECK (run_status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_gd_metadata_harvest_run_source_attempt CHECK (attempt_no BETWEEN 1 AND 3),
    CONSTRAINT ck_gd_metadata_harvest_run_source_count CHECK (collected_table_count >= 0),
    CONSTRAINT ck_gd_metadata_harvest_run_source_time CHECK (completed_time IS NULL OR completed_time >= started_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集运行单数据源分项';

CREATE TABLE gd_metadata_harvest_raw_result
(
    raw_result_id        bigint       NOT NULL COMMENT '原始结果主键，雪花ID',
    tenant_id            varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    run_source_id        bigint       NOT NULL COMMENT '运行数据源分项主键',
    object_storage_ref   varchar(500) NOT NULL COMMENT '受控对象存储引用',
    content_hash         char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256内容哈希',
    content_size         bigint       NOT NULL COMMENT '内容字节数',
    content_format       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内容格式',
    retention_expire_time datetime(3) NOT NULL COMMENT '保留到期时间',
    trace_id             varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    create_dept          bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by            bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time          datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by            bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time          datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (raw_result_id),
    UNIQUE KEY uk_gd_metadata_harvest_raw_source (tenant_id, run_source_id),
    KEY idx_gd_metadata_harvest_raw_expire (tenant_id, retention_expire_time, raw_result_id),
    CONSTRAINT ck_gd_metadata_harvest_raw_size CHECK (content_size >= 0),
    CONSTRAINT ck_gd_metadata_harvest_raw_format CHECK (content_format IN ('JSON', 'JSON_GZIP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集原始结果受控归档引用';

SELECT COUNT(*) AS govdata_metadata_harvest_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'gd_metadata_harvest_plan', 'gd_metadata_harvest_plan_source', 'gd_metadata_harvest_rule',
    'gd_metadata_harvest_run', 'gd_metadata_harvest_run_source', 'gd_metadata_harvest_raw_result'
  );

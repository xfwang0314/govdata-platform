-- govdata-metadata-harvest baseline (MySQL 8)
SET NAMES utf8mb4;

DROP TABLE IF EXISTS gd_metadata_harvest_raw_result;
DROP TABLE IF EXISTS gd_metadata_harvest_run_failure;
DROP TABLE IF EXISTS gd_metadata_harvest_run;
DROP TABLE IF EXISTS gd_metadata_harvest_rule;
DROP TABLE IF EXISTS gd_metadata_harvest_plan_revision;
DROP TABLE IF EXISTS gd_metadata_harvest_plan;

-- ----------------------------
-- 1、元数据采集计划表
-- ----------------------------
CREATE TABLE gd_metadata_harvest_plan (
    harvest_plan_id bigint NOT NULL COMMENT '采集计划主键，雪花ID',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    authz_resource_id bigint DEFAULT NULL COMMENT '授权资源投影主键',
    harvest_plan_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定计划编码',
    harvest_plan_name varchar(200) NOT NULL COMMENT '计划名称',
    datasource_id bigint NOT NULL COMMENT '绑定的数据源主键',
    datasource_resource_id bigint NOT NULL COMMENT '数据源授权资源主键快照',
    owner_dept_id bigint NOT NULL COMMENT '权属部门ID',
    schedule_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调度类型',
    cron_expression varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Cron表达式',
    plan_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计划状态',
    current_plan_revision_no int NOT NULL COMMENT '当前配置修订号',
    scope_json json DEFAULT NULL COMMENT '受Schema约束的采集范围',
    raw_archive_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否归档原始结果',
    lock_version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept bigint DEFAULT NULL COMMENT '创建部门ID',
    create_by bigint DEFAULT NULL COMMENT '创建人ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by bigint DEFAULT NULL COMMENT '更新人ID',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (harvest_plan_id),
    UNIQUE KEY uk_gd_metadata_harvest_plan_code (tenant_id, harvest_plan_code),
    UNIQUE KEY uk_gd_metadata_harvest_plan_authz (tenant_id, authz_resource_id),
    KEY idx_gd_metadata_harvest_plan_schedule (tenant_id, plan_status, schedule_type, harvest_plan_id),
    KEY idx_gd_metadata_harvest_plan_owner (tenant_id, owner_dept_id, harvest_plan_id),
    CONSTRAINT ck_gd_metadata_harvest_plan_schedule CHECK (schedule_type IN ('MANUAL','CRON')),
    CONSTRAINT ck_gd_metadata_harvest_plan_status CHECK (plan_status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_gd_metadata_harvest_plan_revision CHECK (current_plan_revision_no > 0),
    CONSTRAINT ck_gd_metadata_harvest_plan_archive CHECK (raw_archive_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_harvest_plan_del CHECK (del_flag IN ('0','1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '元数据采集计划';

-- ----------------------------
-- 2、采集计划不可变配置修订表
-- ----------------------------
CREATE TABLE gd_metadata_harvest_plan_revision (
    plan_revision_id bigint NOT NULL COMMENT '计划配置修订主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    harvest_plan_id bigint NOT NULL COMMENT '采集计划主键',
    plan_revision_no int NOT NULL COMMENT '计划内修订号',
    datasource_id bigint NOT NULL COMMENT '数据源快照',
    schedule_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调度类型快照',
    cron_expression varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Cron快照',
    scope_json json DEFAULT NULL COMMENT '采集范围快照',
    raw_archive_flag tinyint(1) NOT NULL COMMENT '归档开关快照',
    config_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '配置SHA-256',
    actor_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '修改主体类型',
    actor_id varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '修改主体ID',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (plan_revision_id),
    UNIQUE KEY uk_gd_metadata_harvest_plan_revision (tenant_id, harvest_plan_id, plan_revision_no),
    KEY idx_gd_metadata_harvest_plan_revision_time (tenant_id, harvest_plan_id, create_time, plan_revision_id),
    CONSTRAINT ck_gd_metadata_harvest_plan_revision_no CHECK (plan_revision_no > 0),
    CONSTRAINT ck_gd_metadata_harvest_plan_revision_schedule CHECK (schedule_type IN ('MANUAL','CRON')),
    CONSTRAINT ck_gd_metadata_harvest_plan_revision_archive CHECK (raw_archive_flag IN (0,1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集计划不可变配置修订';

-- ----------------------------
-- 3、采集计划配置修订过滤规则表
-- ----------------------------
CREATE TABLE gd_metadata_harvest_rule (
    harvest_rule_id bigint NOT NULL COMMENT '采集规则主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    plan_revision_id bigint NOT NULL COMMENT '计划配置修订主键',
    rule_scope varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则作用域',
    rule_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '包含或排除',
    match_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配方式',
    rule_pattern varchar(500) COLLATE utf8mb4_bin NOT NULL COMMENT '名称匹配文本或受控正则表达式',
    sort_no int NOT NULL DEFAULT 0 COMMENT '规则顺序',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (harvest_rule_id),
    UNIQUE KEY uk_gd_metadata_harvest_rule (tenant_id, plan_revision_id, rule_scope, rule_type, match_type, rule_pattern),
    KEY idx_gd_metadata_harvest_rule_order (tenant_id, plan_revision_id, sort_no, harvest_rule_id),
    CONSTRAINT ck_gd_metadata_harvest_rule_scope CHECK (rule_scope IN ('CATALOG','SCHEMA','TABLE')),
    CONSTRAINT ck_gd_metadata_harvest_rule_type CHECK (rule_type IN ('INCLUDE','EXCLUDE')),
    CONSTRAINT ck_gd_metadata_harvest_match_type CHECK (match_type IN ('EXACT','PREFIX','SUFFIX','CONTAINS','REGEX')),
    CONSTRAINT ck_gd_metadata_harvest_rule_order CHECK (sort_no >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集计划配置修订过滤规则';

-- ----------------------------
-- 4、单数据源元数据采集运行表
-- ----------------------------
CREATE TABLE gd_metadata_harvest_run (
    harvest_run_id bigint NOT NULL COMMENT '采集运行主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    harvest_run_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定运行编号',
    harvest_plan_id bigint NOT NULL COMMENT '采集计划主键',
    plan_revision_id bigint NOT NULL COMMENT '执行配置修订主键',
    datasource_id bigint NOT NULL COMMENT '数据源快照',
    datasource_resource_id bigint NOT NULL COMMENT '数据源资源快照',
    owner_dept_id bigint NOT NULL COMMENT '权属部门快照',
    connector_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Connector编码',
    connector_version varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Connector版本',
    trigger_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '触发类型',
    trigger_ref varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '触发引用',
    actor_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
    actor_id varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体ID',
    run_status varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行状态',
    active_datasource_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '运行中数据源互斥键',
    complete_run_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否完整成功',
    discovered_table_count int NOT NULL DEFAULT 0 COMMENT '发现表数',
    changed_table_count int NOT NULL DEFAULT 0 COMMENT '变化表数',
    failed_table_count int NOT NULL DEFAULT 0 COMMENT '失败表数',
    active_table_count int NOT NULL DEFAULT 0 COMMENT '缺失判断前活动表数',
    missing_candidate_count int NOT NULL DEFAULT 0 COMMENT '候选消失表数',
    missing_marked_count int NOT NULL DEFAULT 0 COMMENT '已标记消失表数',
    missing_suppressed_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否因异常比例暂停缺失生效',
    started_time datetime(3) NOT NULL COMMENT '开始时间',
    completed_time datetime(3) DEFAULT NULL COMMENT '完成时间',
    failure_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定失败编码',
    failure_summary varchar(1000) DEFAULT NULL COMMENT '脱敏失败摘要',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (harvest_run_id),
    UNIQUE KEY uk_gd_metadata_harvest_run_no (tenant_id, harvest_run_no),
    UNIQUE KEY uk_gd_metadata_harvest_trigger (tenant_id, harvest_plan_id, trigger_type, trigger_ref),
    UNIQUE KEY uk_gd_metadata_harvest_active_source (tenant_id, active_datasource_key),
    KEY idx_gd_metadata_harvest_run_plan (tenant_id, harvest_plan_id, started_time, harvest_run_id),
    KEY idx_gd_metadata_harvest_run_source (tenant_id, datasource_id, started_time, harvest_run_id),
    KEY idx_gd_metadata_harvest_run_status (tenant_id, run_status, started_time, harvest_run_id),
    CONSTRAINT ck_gd_metadata_harvest_run_trigger CHECK (trigger_type IN ('MANUAL','CRON','RETRY')),
    CONSTRAINT ck_gd_metadata_harvest_run_status CHECK (run_status IN ('QUEUED','RUNNING','SUCCESS','ANOMALOUS','FAILED','CANCELLED')),
    CONSTRAINT ck_gd_metadata_harvest_run_complete CHECK (complete_run_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_harvest_run_count CHECK (discovered_table_count >= 0 AND changed_table_count >= 0 AND failed_table_count >= 0 AND active_table_count >= 0 AND missing_candidate_count >= 0 AND missing_marked_count >= 0),
    CONSTRAINT ck_gd_metadata_harvest_run_missing_suppressed CHECK (missing_suppressed_flag IN (0,1)),
    CONSTRAINT ck_gd_metadata_harvest_run_time CHECK (completed_time IS NULL OR completed_time >= started_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '单数据源元数据采集运行';

-- ----------------------------
-- 5、采集运行最终失败明细表
-- ----------------------------
CREATE TABLE gd_metadata_harvest_run_failure (
    run_failure_id bigint NOT NULL COMMENT '运行失败明细主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    harvest_run_id bigint NOT NULL COMMENT '采集运行主键',
    failure_scope varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '失败作用域',
    object_key varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败对象定位键',
    attempt_no int NOT NULL DEFAULT 1 COMMENT '尝试次数',
    failure_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定失败编码',
    failure_summary varchar(1000) NOT NULL COMMENT '脱敏失败摘要',
    occurred_time datetime(3) NOT NULL COMMENT '发生时间',
    actor_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
    actor_id varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体ID',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (run_failure_id),
    KEY idx_gd_metadata_harvest_failure_run (tenant_id, harvest_run_id, occurred_time, run_failure_id),
    CONSTRAINT ck_gd_metadata_harvest_failure_scope CHECK (failure_scope IN ('DISCOVERY','NAMESPACE_BATCH','TABLE')),
    CONSTRAINT ck_gd_metadata_harvest_failure_attempt CHECK (attempt_no BETWEEN 1 AND 3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集运行最终失败明细';

-- ----------------------------
-- 6、采集原始批次受控归档引用表
-- ----------------------------
CREATE TABLE gd_metadata_harvest_raw_result (
    raw_result_id bigint NOT NULL COMMENT '原始结果主键',
    tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户编号',
    harvest_run_id bigint NOT NULL COMMENT '采集运行主键',
    batch_no int NOT NULL COMMENT '运行内批次号',
    object_storage_ref varchar(500) NOT NULL COMMENT '对象存储引用',
    content_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内容SHA-256',
    content_size bigint NOT NULL COMMENT '内容字节数',
    content_format varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内容格式',
    retention_expire_time datetime(3) DEFAULT NULL COMMENT '保留到期时间',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路ID',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (raw_result_id),
    UNIQUE KEY uk_gd_metadata_harvest_raw_batch (tenant_id, harvest_run_id, batch_no),
    KEY idx_gd_metadata_harvest_raw_expire (tenant_id, retention_expire_time, raw_result_id),
    CONSTRAINT ck_gd_metadata_harvest_raw_batch CHECK (batch_no > 0),
    CONSTRAINT ck_gd_metadata_harvest_raw_size CHECK (content_size >= 0),
    CONSTRAINT ck_gd_metadata_harvest_raw_format CHECK (content_format IN ('JSON','JSON_GZIP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集原始批次受控归档引用';

SELECT COUNT(*) AS govdata_metadata_harvest_table_count FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('gd_metadata_harvest_plan','gd_metadata_harvest_plan_revision','gd_metadata_harvest_rule','gd_metadata_harvest_run','gd_metadata_harvest_run_failure','gd_metadata_harvest_raw_result');

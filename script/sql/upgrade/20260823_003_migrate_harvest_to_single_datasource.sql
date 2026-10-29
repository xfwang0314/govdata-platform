-- module: govdata-metadata-harvest
-- depends-on: 20260823_002_migrate_metadata_to_table_revisions.sql
-- purpose: 将多数据源计划和运行分项兼容迁移为单数据源计划、配置修订和失败明细
-- expected-rows: 现有采集计划、规则、运行和原始归档行数
-- lock-risk: HIGH
-- downtime-required: YES
-- pre-check: 每个计划最多一个ACTIVE数据源；同一数据源最多属于一个未删除计划；每个运行最多一个run_source
-- post-check: 未删除计划的新数据源列无NULL；每个计划存在一条配置修订；旧分项表保留
-- rollback-or-forward-fix: plan_source与run_source保留不删；失败后停止应用并以前向脚本修复

SET NAMES utf8mb4;

SELECT harvest_plan_id, COUNT(*) AS source_count
FROM gd_metadata_harvest_plan_source
WHERE binding_status = 'ACTIVE'
GROUP BY harvest_plan_id
HAVING COUNT(*) > 1;

SELECT datasource_id, COUNT(DISTINCT harvest_plan_id) AS plan_count
FROM gd_metadata_harvest_plan_source
WHERE binding_status = 'ACTIVE'
GROUP BY datasource_id
HAVING COUNT(DISTINCT harvest_plan_id) > 1;

SELECT harvest_run_id, COUNT(*) AS run_source_count
FROM gd_metadata_harvest_run_source
GROUP BY harvest_run_id
HAVING COUNT(*) > 1;

ALTER TABLE gd_metadata_harvest_plan
    ADD COLUMN datasource_id bigint DEFAULT NULL COMMENT '唯一绑定的数据源主键' AFTER harvest_plan_name,
    ADD COLUMN active_datasource_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '未删除计划的数据源唯一键' AFTER datasource_id,
    ADD COLUMN datasource_resource_id bigint DEFAULT NULL COMMENT '数据源授权资源主键快照' AFTER active_datasource_key,
    ADD COLUMN current_plan_revision_no int DEFAULT NULL COMMENT '当前配置修订号' AFTER plan_status,
    ADD COLUMN scope_json json DEFAULT NULL COMMENT '受Schema约束的采集范围' AFTER current_plan_revision_no,
    ADD COLUMN raw_archive_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否归档原始结果' AFTER scope_json;

UPDATE gd_metadata_harvest_plan plan
INNER JOIN gd_metadata_harvest_plan_source source
        ON source.tenant_id = plan.tenant_id
       AND source.harvest_plan_id = plan.harvest_plan_id
       AND source.binding_status = 'ACTIVE'
INNER JOIN gd_datasource datasource
        ON datasource.tenant_id = source.tenant_id
       AND datasource.datasource_id = source.datasource_id
SET plan.datasource_id = source.datasource_id,
    plan.active_datasource_key = CASE WHEN plan.del_flag = '0' THEN CAST(source.datasource_id AS char) ELSE NULL END,
    plan.datasource_resource_id = datasource.authz_resource_id,
    plan.current_plan_revision_no = 1,
    plan.scope_json = source.scope_json
WHERE plan.harvest_plan_id > 0;

ALTER TABLE gd_metadata_harvest_plan
    MODIFY COLUMN datasource_id bigint NOT NULL COMMENT '唯一绑定的数据源主键',
    MODIFY COLUMN datasource_resource_id bigint NOT NULL COMMENT '数据源授权资源主键快照',
    MODIFY COLUMN current_plan_revision_no int NOT NULL COMMENT '当前配置修订号',
    ADD UNIQUE KEY uk_gd_metadata_harvest_plan_datasource (tenant_id, active_datasource_key);

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
    KEY idx_gd_metadata_harvest_plan_revision_time (tenant_id, harvest_plan_id, create_time, plan_revision_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集计划不可变配置修订';

INSERT INTO gd_metadata_harvest_plan_revision (
    plan_revision_id, tenant_id, harvest_plan_id, plan_revision_no, datasource_id,
    schedule_type, cron_expression, scope_json, raw_archive_flag, config_hash,
    actor_type, actor_id, trace_id, create_time
)
SELECT harvest_plan_id, tenant_id, harvest_plan_id, 1, datasource_id,
       schedule_type, cron_expression, scope_json, raw_archive_flag,
       SHA2(CONCAT_WS('|', datasource_id, schedule_type, COALESCE(cron_expression, ''), COALESCE(CAST(scope_json AS char), '{}')), 256),
       'SYSTEM', 'harvest-migration', NULL, create_time
FROM gd_metadata_harvest_plan;

ALTER TABLE gd_metadata_harvest_rule
    MODIFY COLUMN binding_id bigint DEFAULT NULL COMMENT '旧版计划数据源绑定主键',
    ADD COLUMN plan_revision_id bigint DEFAULT NULL COMMENT '计划配置修订主键' AFTER binding_id;

UPDATE gd_metadata_harvest_rule rule_item
INNER JOIN gd_metadata_harvest_plan_source source
       ON source.tenant_id = rule_item.tenant_id
       AND source.binding_id = rule_item.binding_id
SET rule_item.plan_revision_id = source.harvest_plan_id
WHERE rule_item.harvest_rule_id > 0;

ALTER TABLE gd_metadata_harvest_rule
    MODIFY COLUMN plan_revision_id bigint NOT NULL COMMENT '计划配置修订主键',
    ADD UNIQUE KEY uk_gd_metadata_harvest_rule_revision (
        tenant_id, plan_revision_id, rule_scope, rule_type, match_type, rule_pattern
    );

ALTER TABLE gd_metadata_harvest_run
    ADD COLUMN plan_revision_id bigint DEFAULT NULL COMMENT '执行配置修订主键' AFTER harvest_plan_id,
    ADD COLUMN datasource_id bigint DEFAULT NULL COMMENT '数据源快照' AFTER plan_revision_id,
    ADD COLUMN datasource_resource_id bigint DEFAULT NULL COMMENT '数据源资源快照' AFTER datasource_id,
    ADD COLUMN connector_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Connector编码' AFTER owner_dept_id,
    ADD COLUMN connector_version varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Connector版本' AFTER connector_code,
    ADD COLUMN active_datasource_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '运行中数据源互斥键' AFTER run_status,
    ADD COLUMN complete_run_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否完整成功' AFTER active_datasource_key,
    ADD COLUMN discovered_table_count int NOT NULL DEFAULT 0 COMMENT '发现表数' AFTER complete_run_flag,
    ADD COLUMN changed_table_count int NOT NULL DEFAULT 0 COMMENT '变化表数' AFTER discovered_table_count,
    ADD COLUMN failed_table_count int NOT NULL DEFAULT 0 COMMENT '失败表数' AFTER changed_table_count,
    ADD COLUMN failure_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定失败编码' AFTER completed_time,
    ADD COLUMN failure_summary varchar(1000) DEFAULT NULL COMMENT '脱敏失败摘要' AFTER failure_code;

UPDATE gd_metadata_harvest_run run_item
INNER JOIN gd_metadata_harvest_plan plan
        ON plan.tenant_id = run_item.tenant_id
       AND plan.harvest_plan_id = run_item.harvest_plan_id
LEFT JOIN gd_metadata_harvest_run_source source
       ON source.tenant_id = run_item.tenant_id
      AND source.harvest_run_id = run_item.harvest_run_id
SET run_item.plan_revision_id = plan.harvest_plan_id,
    run_item.datasource_id = plan.datasource_id,
    run_item.datasource_resource_id = plan.datasource_resource_id,
    run_item.connector_code = source.connector_code,
    run_item.connector_version = source.connector_version,
    run_item.complete_run_flag = CASE WHEN run_item.run_status = 'SUCCESS' THEN 1 ELSE 0 END,
    run_item.discovered_table_count = COALESCE(source.collected_table_count, 0),
    run_item.failed_table_count = CASE WHEN source.run_status = 'FAILED' THEN 1 ELSE 0 END,
    run_item.failure_code = source.failure_code,
    run_item.failure_summary = source.failure_summary
WHERE run_item.harvest_run_id > 0;

UPDATE gd_metadata_harvest_run
SET run_status = 'FAILED',
    complete_run_flag = 0,
    failure_code = COALESCE(failure_code, 'HARVEST_LEGACY_PARTIAL_FAILURE'),
    failure_summary = COALESCE(failure_summary, '旧版部分成功运行已迁移为失败，请按失败明细复核后重试')
WHERE run_status = 'PARTIAL_SUCCESS';

ALTER TABLE gd_metadata_harvest_run
    MODIFY COLUMN plan_revision_id bigint NOT NULL COMMENT '执行配置修订主键',
    MODIFY COLUMN datasource_id bigint NOT NULL COMMENT '数据源快照',
    MODIFY COLUMN datasource_resource_id bigint NOT NULL COMMENT '数据源资源快照',
    DROP CHECK ck_gd_metadata_harvest_run_status,
    ADD CONSTRAINT ck_gd_metadata_harvest_run_status CHECK (run_status IN ('QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')),
    ADD UNIQUE KEY uk_gd_metadata_harvest_active_source (tenant_id, active_datasource_key),
    ADD KEY idx_gd_metadata_harvest_run_source (tenant_id, datasource_id, started_time, harvest_run_id);

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
    KEY idx_gd_metadata_harvest_failure_run (tenant_id, harvest_run_id, occurred_time, run_failure_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采集运行最终失败明细';

ALTER TABLE gd_metadata_harvest_raw_result
    MODIFY COLUMN run_source_id bigint DEFAULT NULL COMMENT '旧版运行数据源分项主键',
    ADD COLUMN harvest_run_id bigint DEFAULT NULL COMMENT '采集运行主键' AFTER run_source_id,
    ADD COLUMN batch_no int DEFAULT NULL COMMENT '运行内批次号' AFTER harvest_run_id;

UPDATE gd_metadata_harvest_raw_result raw_result
INNER JOIN gd_metadata_harvest_run_source source
       ON source.tenant_id = raw_result.tenant_id
       AND source.run_source_id = raw_result.run_source_id
SET raw_result.harvest_run_id = source.harvest_run_id,
    raw_result.batch_no = 1
WHERE raw_result.raw_result_id > 0;

ALTER TABLE gd_metadata_harvest_raw_result
    MODIFY COLUMN harvest_run_id bigint NOT NULL COMMENT '采集运行主键',
    MODIFY COLUMN batch_no int NOT NULL COMMENT '运行内批次号',
    ADD UNIQUE KEY uk_gd_metadata_harvest_raw_batch (tenant_id, harvest_run_id, batch_no);

SELECT COUNT(*) AS plan_without_current_revision
FROM gd_metadata_harvest_plan plan
LEFT JOIN gd_metadata_harvest_plan_revision revision_item
       ON revision_item.tenant_id = plan.tenant_id
      AND revision_item.harvest_plan_id = plan.harvest_plan_id
      AND revision_item.plan_revision_no = plan.current_plan_revision_no
WHERE revision_item.plan_revision_id IS NULL;

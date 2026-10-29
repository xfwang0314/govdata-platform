-- module: govdata-metadata / govdata-metadata-harvest
-- depends-on: 20260823_003_migrate_harvest_to_single_datasource.sql
-- purpose: 增加异常消失保护、治理权限、问题类型和下游影响投影
-- expected-rows: 既有变更问题回填问题类型；新增1条菜单权限
-- lock-risk: MEDIUM
-- downtime-required: YES
-- pre-check: 元数据八张表和采集六张表存在，采集运行当前无RUNNING记录
-- post-check: 运行安全字段、问题类型、影响投影表和治理权限存在
-- rollback-or-forward-fix: 新字段被应用写入后只允许前向修复；影响投影表可由来源模块重建

SET NAMES utf8mb4;

SELECT COUNT(*) AS running_harvest_count
FROM gd_metadata_harvest_run
WHERE run_status = 'RUNNING';

ALTER TABLE gd_metadata_harvest_run
    ADD COLUMN active_table_count int NOT NULL DEFAULT 0 COMMENT '缺失判断前活动表数' AFTER failed_table_count,
    ADD COLUMN missing_candidate_count int NOT NULL DEFAULT 0 COMMENT '候选消失表数' AFTER active_table_count,
    ADD COLUMN missing_marked_count int NOT NULL DEFAULT 0 COMMENT '已标记消失表数' AFTER missing_candidate_count,
    ADD COLUMN missing_suppressed_flag tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否因异常比例暂停缺失生效' AFTER missing_marked_count,
    DROP CHECK ck_gd_metadata_harvest_run_status,
    DROP CHECK ck_gd_metadata_harvest_run_count,
    ADD CONSTRAINT ck_gd_metadata_harvest_run_status CHECK (run_status IN ('QUEUED','RUNNING','SUCCESS','ANOMALOUS','FAILED','CANCELLED')),
    ADD CONSTRAINT ck_gd_metadata_harvest_run_count CHECK (discovered_table_count >= 0 AND changed_table_count >= 0 AND failed_table_count >= 0 AND active_table_count >= 0 AND missing_candidate_count >= 0 AND missing_marked_count >= 0),
    ADD CONSTRAINT ck_gd_metadata_harvest_run_missing_suppressed CHECK (missing_suppressed_flag IN (0,1));

ALTER TABLE gd_metadata_change_issue
    ADD COLUMN issue_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STRUCTURE_BREAKING' COMMENT '问题类型' AFTER issue_code;

ALTER TABLE gd_metadata_change_issue
    MODIFY COLUMN issue_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '问题类型',
    ADD CONSTRAINT ck_gd_metadata_change_issue_type CHECK (issue_type IN ('STRUCTURE_BREAKING','TABLE_MISSING'));

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

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 20202, '治理资产', 20200, 2, '', '', '', 1, 0,
       'F', '0', '0', 'govdata:metadata:edit', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 20202);

SELECT COUNT(*) AS metadata_impact_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'gd_metadata_impact_projection';

SELECT COUNT(*) AS metadata_governance_permission_count
FROM sys_menu
WHERE menu_id = 20202
  AND perms = 'govdata:metadata:edit';

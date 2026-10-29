-- module: govdata-query
-- depends-on: 20260826_001_complete_metadata_governance_and_impact.sql
-- purpose: 新增查询访问追加审计；先迁移再部署查询执行器
-- expected-rows: 新表0行，不修改已有数据
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 确认gd_query_access_log不存在，已备份并记录脚本SHA-256
-- post-check: SHOW CREATE TABLE gd_query_access_log；确认所有CHECK和三个二级索引
-- rollback-or-forward-fix: 优先前向修复；应用回退保留审计表及记录，不自动删表

-- =====================================================================
-- 01. 查询访问审计（govdata-query，追加记录）
-- =====================================================================
CREATE TABLE gd_query_access_log (
    access_log_id bigint NOT NULL COMMENT '访问事件雪花主键',
    tenant_id varchar(20) NOT NULL COMMENT '发起主体租户ID',
    query_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端生成的查询关联ID',
    datasource_id bigint DEFAULT NULL COMMENT '请求数据源ID，未解析时为空',
    resource_id bigint DEFAULT NULL COMMENT '请求授权资源ID，未解析时为空',
    action_code varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '查询动作',
    purpose varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '合法用途编码，无效输入不保存原文',
    actor_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
    actor_id varchar(100) NOT NULL COMMENT '主体稳定ID',
    trace_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '链路关联ID',
    result varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'STARTED/SUCCEEDED/DENIED/FAILED',
    error_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定错误码',
    field_count int NOT NULL COMMENT '请求字段数量，不保存参数和结果',
    filter_count int NOT NULL COMMENT '请求条件数量',
    returned_row_count int NOT NULL COMMENT '允许返回行数，开始和失败为零',
    duration_ms bigint NOT NULL COMMENT '耗时毫秒',
    occurred_time datetime(3) NOT NULL COMMENT '事件时间，Asia/Shanghai',
    PRIMARY KEY (access_log_id),
    KEY idx_gd_query_access_time (tenant_id, occurred_time, access_log_id) COMMENT '租户时间范围检索和归档',
    KEY idx_gd_query_access_query (tenant_id, query_id, access_log_id) COMMENT '查询开始与结束事件关联',
    KEY idx_gd_query_access_resource (tenant_id, resource_id, occurred_time, access_log_id) COMMENT '资源访问追溯',
    CONSTRAINT ck_gd_query_access_actor CHECK (actor_type IN ('HUMAN', 'SERVICE', 'AGENT')),
    CONSTRAINT ck_gd_query_access_result CHECK (result IN ('STARTED', 'SUCCEEDED', 'DENIED', 'FAILED')),
    CONSTRAINT ck_gd_query_access_action CHECK (action_code = 'QUERY'),
    CONSTRAINT ck_gd_query_access_counts CHECK (field_count >= 0 AND filter_count >= 0 AND returned_row_count BETWEEN 0 AND 1000 AND duration_ms >= 0),
    CONSTRAINT ck_gd_query_access_rows CHECK (result = 'SUCCEEDED' OR returned_row_count = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控查询访问追加事件';

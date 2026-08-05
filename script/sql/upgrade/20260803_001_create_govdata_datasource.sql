-- ============================================================================
-- 模块：govdata-datasource
-- 前置：platform-auth 的 authz_resource 已初始化
-- 影响：新增 gd_datasource 与 gd_datasource_connection_test 两张空表
-- 锁表风险：仅创建新表，不扫描或修改现有业务表
-- 回滚：确认无业务数据后，按测试表、实例表顺序 DROP TABLE
-- 验证：脚本末尾 table_count 应为 2，foreign_key_count 应为 0
-- ============================================================================

CREATE TABLE IF NOT EXISTS gd_datasource
(
    datasource_id        bigint        NOT NULL COMMENT '数据源主键，雪花ID',
    tenant_id            varchar(20)   NOT NULL COMMENT '地区租户编号',
    authz_resource_id    bigint        DEFAULT NULL COMMENT 'platform-auth资源投影主键',
    datasource_code      varchar(64)   NOT NULL COMMENT '租户内稳定数据源编码',
    datasource_name      varchar(100)  NOT NULL COMMENT '数据源名称',
    connector_code       varchar(32)   NOT NULL COMMENT 'Connector稳定编码',
    connector_version    varchar(32)   NOT NULL COMMENT 'Connector配置契约版本，由Catalog写入',
    endpoint_config_json json          NOT NULL COMMENT '非敏感端点配置JSON',
    secret_ref           varchar(200)  NOT NULL COMMENT '凭据引用，禁止保存凭据原文',
    environment          varchar(16)   NOT NULL COMMENT '环境：DEV/TEST/PROD',
    owner_dept_id        bigint        NOT NULL COMMENT '权属部门ID',
    access_mode          varchar(16)   NOT NULL DEFAULT 'READ_ONLY' COMMENT '访问模式：READ_ONLY/READ_WRITE',
    classification_code varchar(32)   NOT NULL DEFAULT 'INTERNAL' COMMENT '数据分类编码',
    security_level       int           NOT NULL DEFAULT 1 COMMENT '安全等级1-5',
    status               varchar(32)   NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ACTIVE/DISABLED',
    last_test_status     varchar(32)   NOT NULL DEFAULT 'NOT_TESTED' COMMENT '最近测试状态',
    last_test_time       datetime(3)   DEFAULT NULL COMMENT '最近测试时间',
    lock_version         int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag             char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept          bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by            bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by            bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (datasource_id),
    UNIQUE KEY uk_gd_datasource_code (tenant_id, datasource_code),
    UNIQUE KEY uk_gd_datasource_authz (tenant_id, authz_resource_id),
    KEY idx_gd_datasource_connector (tenant_id, connector_code, status),
    KEY idx_gd_datasource_owner (tenant_id, owner_dept_id, status),
    KEY idx_gd_datasource_environment (tenant_id, environment, status),
    KEY idx_gd_datasource_classification (tenant_id, classification_code, security_level, status),
    KEY idx_gd_datasource_test_status (tenant_id, last_test_status, last_test_time),
    CONSTRAINT ck_gd_datasource_environment CHECK (environment IN ('DEV', 'TEST', 'PROD')),
    CONSTRAINT ck_gd_datasource_access_mode CHECK (access_mode IN ('READ_ONLY', 'READ_WRITE')),
    CONSTRAINT ck_gd_datasource_classification CHECK (classification_code IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED')),
    CONSTRAINT ck_gd_datasource_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_gd_datasource_test_status CHECK (last_test_status IN ('NOT_TESTED', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_gd_datasource_security CHECK (security_level BETWEEN 1 AND 5),
    CONSTRAINT ck_gd_datasource_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '外部数据源连接实例';

CREATE TABLE IF NOT EXISTS gd_datasource_connection_test
(
    test_id             bigint        NOT NULL COMMENT '连接测试主键，雪花ID',
    tenant_id           varchar(20)   NOT NULL COMMENT '地区租户编号',
    datasource_id       bigint        NOT NULL COMMENT '数据源主键',
    connector_code      varchar(32)   NOT NULL COMMENT 'Connector编码快照',
    connector_version   varchar(32)   NOT NULL COMMENT 'Connector版本快照',
    result              varchar(16)   NOT NULL COMMENT '结果：SUCCESS/FAILED',
    reason_code         varchar(64)   NOT NULL COMMENT '稳定原因编码',
    latency_ms          bigint        NOT NULL COMMENT '测试耗时毫秒',
    server_info_json    json          DEFAULT NULL COMMENT '脱敏服务端信息JSON',
    actor_type          varchar(16)   NOT NULL COMMENT '执行主体类型',
    actor_id            varchar(100)  NOT NULL COMMENT '执行主体稳定ID',
    trace_id            varchar(64)   DEFAULT NULL COMMENT '链路追踪ID',
    occurred_time       datetime(3)   NOT NULL COMMENT '发生时间',
    PRIMARY KEY (test_id),
    KEY idx_gd_datasource_test_source (tenant_id, datasource_id, occurred_time),
    KEY idx_gd_datasource_test_result (tenant_id, result, occurred_time),
    KEY idx_gd_datasource_test_trace (trace_id),
    CONSTRAINT ck_gd_datasource_test_result CHECK (result IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_gd_datasource_test_latency CHECK (latency_ms >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据源连接测试追加记录';

SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('gd_datasource', 'gd_datasource_connection_test');

SELECT COUNT(*) AS foreign_key_count
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('gd_datasource', 'gd_datasource_connection_test')
  AND constraint_type = 'FOREIGN KEY';

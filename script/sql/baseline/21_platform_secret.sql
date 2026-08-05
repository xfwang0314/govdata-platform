-- ============================================================================
-- 政务数据中台 platform-secret 第一版运行基线
-- 数据库：MySQL 8.0；无物理外键；主加密密钥不得进入数据库
-- ============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS platform_secret_access_log;
DROP TABLE IF EXISTS platform_secret_version;
DROP TABLE IF EXISTS platform_secret;

CREATE TABLE platform_secret
(
    secret_id           bigint        NOT NULL COMMENT '凭据主键，雪花ID',
    tenant_id           varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    secret_ref          varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务模块保存的稳定凭据引用',
    secret_code         varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内稳定凭据编码，由服务端生成',
    secret_name         varchar(100)  NOT NULL COMMENT '凭据名称',
    description         varchar(500)  DEFAULT NULL COMMENT '凭据用途、责任边界和维护说明，不得填写敏感值',
    secret_type         varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '类型：USERNAME_PASSWORD/PASSWORD/TOKEN/KEY_VALUE',
    owner_module        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '允许解析该凭据的拥有模块',
    purpose_scope       varchar(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '允许用途编码，逗号分隔或*',
    current_version_id  bigint        DEFAULT NULL COMMENT '当前密文版本主键',
    status              varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    expire_time         datetime(3)   DEFAULT NULL COMMENT '凭据过期时间',
    lock_version        int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag            char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_dept         bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (secret_id),
    UNIQUE KEY uk_platform_secret_ref (tenant_id, secret_ref),
    UNIQUE KEY uk_platform_secret_code (tenant_id, secret_code),
    KEY idx_platform_secret_owner (tenant_id, owner_module, secret_type, status),
    CONSTRAINT ck_platform_secret_type CHECK (secret_type IN ('USERNAME_PASSWORD', 'PASSWORD', 'TOKEN', 'KEY_VALUE')),
    CONSTRAINT ck_platform_secret_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_platform_secret_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台凭据元数据';

CREATE TABLE platform_secret_version
(
    secret_version_id   bigint        NOT NULL COMMENT '凭据版本主键，雪花ID',
    tenant_id           varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    secret_id           bigint        NOT NULL COMMENT '凭据主键',
    version_no          int           NOT NULL COMMENT '版本号，从1递增',
    secret_ciphertext   longtext      NOT NULL COMMENT 'AES-256-GCM密文，不包含主密钥',
    encryption_algorithm varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '加密算法',
    key_version         varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '外部主密钥版本',
    checksum            char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '密文SHA-256校验值',
    effective_time      datetime(3)   NOT NULL COMMENT '生效时间',
    expire_time         datetime(3)   DEFAULT NULL COMMENT '版本过期时间',
    status              varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态：ACTIVE/DISABLED',
    create_dept         bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (secret_version_id),
    UNIQUE KEY uk_platform_secret_version (tenant_id, secret_id, version_no),
    KEY idx_platform_secret_version_status (tenant_id, secret_id, status),
    CONSTRAINT ck_platform_secret_version_no CHECK (version_no > 0),
    CONSTRAINT ck_platform_secret_version_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台凭据不可变密文版本';

CREATE TABLE platform_secret_access_log
(
    access_log_id       bigint        NOT NULL COMMENT '访问日志主键，雪花ID',
    tenant_id           varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    secret_id           bigint        DEFAULT NULL COMMENT '凭据主键，解析前失败时可为空',
    secret_ref_snapshot varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '凭据引用快照',
    caller_module       varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用模块',
    actor_type          varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
    actor_id            varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体稳定ID',
    purpose             varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '解析用途编码',
    result              varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结果：SUCCESS/FAILED',
    reason_code         varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定原因编码',
    trace_id            varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    occurred_time       datetime(3)   NOT NULL COMMENT '发生时间',
    create_dept         bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (access_log_id),
    KEY idx_platform_secret_access_secret (tenant_id, secret_id, occurred_time),
    KEY idx_platform_secret_access_caller (tenant_id, caller_module, result, occurred_time),
    KEY idx_platform_secret_access_trace (trace_id),
    CONSTRAINT ck_platform_secret_access_result CHECK (result IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台凭据访问审计';

SELECT COUNT(*) AS platform_secret_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('platform_secret', 'platform_secret_version', 'platform_secret_access_log');

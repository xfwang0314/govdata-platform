-- ============================================================================
-- 政务数据中台 platform-auth 第一版运行基线
-- 数据库：MySQL 8.0
-- 状态：第一版空库初始化脚本；仅适用于 MySQL 8.0
-- 范围：Human Resource、不可变 Policy、AuthorizationGrant、Obligation 与授权审计
-- 约束：不建立物理外键；引用完整性由应用服务、事务、索引、对账任务和集成测试保证
-- ============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS authz_decision_log;
DROP TABLE IF EXISTS authz_grant_scope;
DROP TABLE IF EXISTS authz_grant_resource;
DROP TABLE IF EXISTS authz_grant;
DROP TABLE IF EXISTS authz_policy_runtime_resource;
DROP TABLE IF EXISTS authz_policy_runtime_subject;
DROP TABLE IF EXISTS authz_policy_runtime_rule;
DROP TABLE IF EXISTS authz_policy_operation_log;
DROP TABLE IF EXISTS authz_policy_group_version;
DROP TABLE IF EXISTS authz_policy_action;
DROP TABLE IF EXISTS authz_policy_resource;
DROP TABLE IF EXISTS authz_policy_subject;
DROP TABLE IF EXISTS authz_policy_version;
DROP TABLE IF EXISTS authz_policy;
DROP TABLE IF EXISTS authz_policy_group;
DROP TABLE IF EXISTS authz_resource_relation;
DROP TABLE IF EXISTS authz_resource;

CREATE TABLE authz_resource
(
    resource_id          bigint        NOT NULL COMMENT '资源主键，雪花ID',
    tenant_id            varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    resource_code        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后端生成的稳定资源编码',
    resource_name        varchar(200)  NOT NULL COMMENT '资源名称',
    resource_type        varchar(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源类型编码',
    parent_id            bigint        DEFAULT NULL COMMENT '父资源主键',
    source_module        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源模块',
    business_id          varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源模块稳定业务ID',
    owner_dept_id        bigint        DEFAULT NULL COMMENT '权属部门ID',
    owner_user_id        bigint        DEFAULT NULL COMMENT '业务资源所有人ID',
    owner_dept_path_json json          DEFAULT NULL COMMENT '权属部门祖先及自身ID路径JSON',
    region_code          varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '行政区划编码',
    classification_code  varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '安全分类编码',
    security_level       int           DEFAULT NULL COMMENT '安全等级',
    attributes_json      json          DEFAULT NULL COMMENT '已注册扩展授权属性JSON',
    inherit_policy       tinyint(1)    NOT NULL DEFAULT 1 COMMENT '是否继承父资源策略',
    status               varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态：ACTIVE/DISABLED/ARCHIVED',
    lock_version         int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag             char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept          bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by            bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by            bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (resource_id),
    UNIQUE KEY uk_authz_resource_code (tenant_id, resource_code),
    UNIQUE KEY uk_authz_resource_business (tenant_id, source_module, resource_type, business_id),
    KEY idx_authz_resource_parent (tenant_id, parent_id),
    KEY idx_authz_resource_type_status (tenant_id, resource_type, status),
    KEY idx_authz_resource_owner (tenant_id, owner_dept_id, status),
    KEY idx_authz_resource_owner_user (tenant_id, owner_user_id, status),
    CONSTRAINT ck_authz_resource_status CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_authz_resource_security CHECK (security_level IS NULL OR security_level BETWEEN 1 AND 5),
    CONSTRAINT ck_authz_resource_classification
        CHECK (classification_code IS NULL OR classification_code IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED')),
    CONSTRAINT ck_authz_resource_inherit CHECK (inherit_policy IN (0, 1)),
    CONSTRAINT ck_authz_resource_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '统一可授权资源投影';

CREATE TABLE authz_resource_relation
(
    relation_id        bigint       NOT NULL COMMENT '资源关系主键，雪花ID',
    tenant_id          varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    source_resource_id bigint       NOT NULL COMMENT '源资源主键',
    target_resource_id bigint       NOT NULL COMMENT '目标资源主键',
    relation_type      varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定关系类型编码',
    create_dept        bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by          bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (relation_id),
    UNIQUE KEY uk_authz_relation_edge
        (tenant_id, source_resource_id, target_resource_id, relation_type),
    KEY idx_authz_relation_source (tenant_id, source_resource_id, relation_type),
    KEY idx_authz_relation_target (tenant_id, target_resource_id, relation_type),
    CONSTRAINT ck_authz_relation_self CHECK (source_resource_id <> target_resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '授权资源非层级关系';

CREATE TABLE authz_policy_group
(
    policy_group_id      bigint       NOT NULL COMMENT '策略组主键，雪花ID',
    tenant_id            varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_group_code    varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后端生成的稳定策略组编码',
    status               varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行状态：DRAFT/ACTIVE/DISABLED',
    current_version_id   bigint       DEFAULT NULL COMMENT '当前运行版本主键',
    draft_version_id     bigint       DEFAULT NULL COMMENT '唯一待发布版本主键，字段名为兼容保留',
    next_version_no      int          NOT NULL DEFAULT 1 COMMENT '下一个可分配版本号，已分配后不回退',
    creation_source      varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建来源：CUSTOM/TEMPLATE',
    source_template_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '一次性待发布版本模板来源编码',
    deleted_by           bigint       DEFAULT NULL COMMENT '逻辑删除操作人ID',
    deleted_time         datetime(3)  DEFAULT NULL COMMENT '逻辑删除时间',
    delete_reason        varchar(500) DEFAULT NULL COMMENT '逻辑删除原因',
    lock_version         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag             char(1)      NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept          bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by            bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time          datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by            bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time          datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (policy_group_id),
    UNIQUE KEY uk_authz_policy_group_code (tenant_id, policy_group_code),
    KEY idx_authz_policy_group_status (tenant_id, del_flag, status, update_time),
    KEY idx_authz_policy_group_source (tenant_id, creation_source, update_time),
    CONSTRAINT ck_authz_policy_group_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_authz_policy_group_source CHECK (creation_source IN ('CUSTOM', 'TEMPLATE')),
    CONSTRAINT ck_authz_policy_group_next_version CHECK (next_version_no > 0),
    CONSTRAINT ck_authz_policy_group_version_pointer CHECK
        (current_version_id IS NULL OR draft_version_id IS NULL OR current_version_id <> draft_version_id),
    CONSTRAINT ck_authz_policy_group_deleted CHECK
        ((del_flag = '0' AND deleted_time IS NULL) OR (del_flag = '1' AND deleted_time IS NOT NULL)),
    CONSTRAINT ck_authz_policy_group_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ABAC策略生命周期与版本管理组';

CREATE TABLE authz_policy_group_version
(
    policy_group_version_id bigint NOT NULL COMMENT '策略组版本主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_group_id   bigint       NOT NULL COMMENT '策略组主键',
    version_no        int          NOT NULL COMMENT '递增版本号',
    schema_version    int          NOT NULL COMMENT '策略文档Schema版本',
    policy_name_snapshot varchar(200) NOT NULL COMMENT '该版本策略名称快照',
    description_snapshot varchar(1000) DEFAULT NULL COMMENT '该版本策略说明快照',
    risk_level        varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该版本风险等级：LOW/MEDIUM/HIGH/CRITICAL',
    effect            varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '策略效果：PERMIT/DENY',
    policy_document_json json     NOT NULL COMMENT '完整不可变策略文档，关联表可由此重建',
    priority          int          NOT NULL DEFAULT 0 COMMENT '策略优先级，值越大优先级越高',
    version_status    varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT/PUBLISHED/SUPERSEDED/DISCARDED',
    content_hash      char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本及全部子项规范化SHA-256',
    catalog_snapshot_json json     NOT NULL COMMENT '资源、动作、目的、属性和义务目录显示快照',
    created_by        bigint       DEFAULT NULL COMMENT '版本创建人ID',
    created_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本创建时间',
    published_by      bigint       DEFAULT NULL COMMENT '版本发布人ID',
    published_time    datetime(3)  DEFAULT NULL COMMENT '版本发布时间',
    lock_version      int          NOT NULL DEFAULT 0 COMMENT '待发布版本并发编辑乐观锁版本',
    PRIMARY KEY (policy_group_version_id),
    UNIQUE KEY uk_authz_policy_ver_no (tenant_id, policy_group_id, version_no),
    KEY idx_authz_policy_ver_hash (tenant_id, policy_group_id, content_hash),
    KEY idx_authz_policy_ver_status (tenant_id, policy_group_id, version_status),
    CONSTRAINT ck_authz_policy_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_authz_policy_ver_no CHECK (version_no > 0),
    CONSTRAINT ck_authz_policy_ver_effect CHECK (effect IN ('PERMIT', 'DENY')),
    CONSTRAINT ck_authz_policy_ver_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_authz_policy_ver_status
        CHECK (version_status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'DISCARDED')),
    CONSTRAINT ck_authz_policy_ver_publish
        CHECK (version_status <> 'PUBLISHED' OR published_time IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可变ABAC策略组版本';

CREATE TABLE authz_policy_operation_log
(
    operation_log_id     bigint       NOT NULL COMMENT '策略操作日志主键，雪花ID',
    tenant_id            varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_group_id      bigint       NOT NULL COMMENT '策略组主键',
    policy_group_code    varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '策略组稳定编码快照',
    policy_version_id    bigint       DEFAULT NULL COMMENT '本次操作关联的策略版本主键',
    operation_type       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作类型',
    from_status          varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '操作前聚合状态',
    to_status            varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '操作后聚合状态',
    operation_reason     varchar(500) DEFAULT NULL COMMENT '操作原因',
    operation_detail_json json        DEFAULT NULL COMMENT '变更字段、版本号等结构化上下文',
    before_content_hash   char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '变更前内容哈希',
    after_content_hash    char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '变更后内容哈希',
    operator_type        varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作主体类型',
    operator_id          varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作主体稳定ID',
    trace_id             varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    occurred_time        datetime(3)  NOT NULL COMMENT '操作发生时间',
    PRIMARY KEY (operation_log_id),
    KEY idx_authz_policy_operation_policy (tenant_id, policy_group_id, occurred_time),
    KEY idx_authz_policy_operation_version (tenant_id, policy_version_id, occurred_time),
    KEY idx_authz_policy_operation_trace (trace_id),
    CONSTRAINT ck_authz_policy_operation_type CHECK (operation_type IN
        ('CREATE_DRAFT', 'UPDATE_DRAFT', 'CREATE_VERSION', 'PUBLISH', 'DISABLE', 'ENABLE',
         'DISCARD_DRAFT', 'DELETE', 'RESTORE', 'ROLLBACK_DRAFT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略生命周期追加操作日志';

CREATE TABLE authz_policy_runtime_rule
(
    runtime_rule_id  bigint       NOT NULL COMMENT '运行时规则主键，雪花ID',
    tenant_id        varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_group_id  bigint       NOT NULL COMMENT '策略组主键',
    policy_group_version_id bigint NOT NULL COMMENT '当前发布策略组版本主键',
    rule_code        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本内稳定规则编码',
    effect           varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PERMIT/DENY',
    priority         int          NOT NULL COMMENT '规则所属策略组优先级',
    resource_type    varchar(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源类型编码',
    resource_scope   varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TYPE/INSTANCE',
    action_codes_json json        NOT NULL COMMENT '动作编码集合',
    purpose_codes_json json       NOT NULL COMMENT '访问目的编码集合',
    condition_json   json         NOT NULL COMMENT '受限条件AST',
    obligation_json  json         DEFAULT NULL COMMENT '结构化义务',
    create_by        bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (runtime_rule_id),
    UNIQUE KEY uk_authz_runtime_rule (tenant_id, policy_group_version_id, rule_code),
    KEY idx_authz_runtime_rule_candidate (tenant_id, resource_type, effect, priority),
    CONSTRAINT ck_authz_runtime_rule_effect CHECK (effect IN ('PERMIT', 'DENY')),
    CONSTRAINT ck_authz_runtime_rule_scope CHECK (resource_scope IN ('TYPE', 'INSTANCE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '当前策略版本编译规则索引';

CREATE TABLE authz_policy_runtime_subject
(
    runtime_subject_id bigint      NOT NULL COMMENT '运行时主体索引主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_group_id   bigint       NOT NULL COMMENT '策略组主键',
    policy_group_version_id bigint NOT NULL COMMENT '当前发布策略组版本主键',
    subject_type      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
    subject_id        varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体稳定ID，ANY为*',
    include_children  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '部门主体是否包含下级部门',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (runtime_subject_id),
    UNIQUE KEY uk_authz_runtime_subject
        (tenant_id, policy_group_version_id, subject_type, subject_id),
    KEY idx_authz_runtime_subject_lookup
        (tenant_id, subject_type, subject_id, policy_group_version_id),
    CONSTRAINT ck_authz_runtime_subject_type
        CHECK (subject_type IN ('ANY', 'HUMAN', 'ROLE', 'DEPARTMENT', 'SERVICE', 'AGENT')),
    CONSTRAINT ck_authz_runtime_subject_children CHECK (include_children IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '当前策略版本主体候选索引';

CREATE TABLE authz_policy_runtime_resource
(
    runtime_resource_id bigint     NOT NULL COMMENT '运行时资源索引主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    runtime_rule_id   bigint       NOT NULL COMMENT '运行时规则主键',
    resource_id       bigint       NOT NULL COMMENT '指定资源主键',
    include_children  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否包含后代资源',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (runtime_resource_id),
    UNIQUE KEY uk_authz_runtime_resource (tenant_id, runtime_rule_id, resource_id),
    KEY idx_authz_runtime_resource_lookup (tenant_id, resource_id, runtime_rule_id),
    CONSTRAINT ck_authz_runtime_resource_children CHECK (include_children IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '当前策略版本资源实例索引';

CREATE TABLE authz_grant
(
    grant_id          bigint        NOT NULL COMMENT 'AuthorizationGrant主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    grant_code        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后端生成的稳定授权编码',
    source_type       varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源类型：SHARING/ADMIN/SUBSCRIPTION/DELEGATION/SYSTEM',
    source_id         varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源业务稳定ID',
    grantor_type      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权方类型：HUMAN/SERVICE/DEPARTMENT/SYSTEM',
    grantor_id        varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权方稳定ID',
    grantee_type      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被授权方类型：HUMAN/ROLE/DEPARTMENT/SERVICE/AGENT',
    grantee_id        varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被授权方稳定ID',
    purpose           varchar(500)  NOT NULL COMMENT '授权目录注册的稳定用途编码',
    effective_time    datetime(3)   NOT NULL COMMENT '生效时间',
    expire_time       datetime(3)   DEFAULT NULL COMMENT '失效时间',
    condition_json    json          DEFAULT NULL COMMENT '附加受限ABAC条件',
    status            varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态：DRAFT/PENDING/ACTIVE/EXPIRED/REVOKED/ARCHIVED',
    revoked_by        bigint        DEFAULT NULL COMMENT '撤销人ID',
    revoked_time      datetime(3)   DEFAULT NULL COMMENT '撤销时间',
    revoke_reason     varchar(1000) DEFAULT NULL COMMENT '撤销原因',
    lock_version      int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag          char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept       bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by         bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by         bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time       datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (grant_id),
    UNIQUE KEY uk_authz_grant_code (tenant_id, grant_code),
    UNIQUE KEY uk_authz_grant_source (tenant_id, source_type, source_id),
    KEY idx_authz_grant_grantee (tenant_id, grantee_type, grantee_id, status),
    KEY idx_authz_grant_validity (tenant_id, status, effective_time, expire_time),
    CONSTRAINT ck_authz_grant_source
        CHECK (source_type IN ('SHARING', 'ADMIN', 'SUBSCRIPTION', 'DELEGATION', 'SYSTEM')),
    CONSTRAINT ck_authz_grant_grantor
        CHECK (grantor_type IN ('HUMAN', 'SERVICE', 'DEPARTMENT', 'SYSTEM')),
    CONSTRAINT ck_authz_grant_grantee
        CHECK (grantee_type IN ('HUMAN', 'ROLE', 'DEPARTMENT', 'SERVICE', 'AGENT')),
    CONSTRAINT ck_authz_grant_status
        CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED', 'ARCHIVED')),
    CONSTRAINT ck_authz_grant_validity CHECK (expire_time IS NULL OR expire_time > effective_time),
    CONSTRAINT ck_authz_grant_revoked
        CHECK (status <> 'REVOKED' OR revoked_time IS NOT NULL),
    CONSTRAINT ck_authz_grant_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '租户内运行时资源授权';

CREATE TABLE authz_grant_scope
(
    scope_id          bigint       NOT NULL COMMENT 'Grant范围主键，雪花ID',
    tenant_id         varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    grant_id          bigint       NOT NULL COMMENT 'AuthorizationGrant主键',
    resource_id       bigint       NOT NULL COMMENT '资源主键',
    action_code       varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定授权动作编码',
    include_children  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否包含后代资源',
    obligation_json   json         DEFAULT NULL COMMENT '结构化行列、脱敏、限量和导出约束',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_authz_grant_scope (tenant_id, grant_id, resource_id, action_code),
    KEY idx_authz_grant_scope_resource (tenant_id, resource_id, action_code, grant_id),
    CONSTRAINT ck_authz_grant_scope_children CHECK (include_children IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Grant资源动作与Obligation范围';



CREATE TABLE authz_decision_log
(
    decision_id             bigint        NOT NULL COMMENT '授权决策日志主键，雪花ID',
    tenant_id               varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审计归属租户，通常为资源租户',
    subject_tenant_id       varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体租户编号',
    subject_type            varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型：HUMAN/SERVICE/AGENT',
    subject_id              varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体稳定ID',
    subject_snapshot_json   json          NOT NULL COMMENT '最小主体属性快照',
    resource_tenant_id      varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源租户编号',
    resource_id             bigint        DEFAULT NULL COMMENT '资源主键',
    resource_type_snapshot  varchar(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源类型快照',
    resource_code_snapshot  varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源编码快照',
    action_code             varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权动作编码',
    decision                varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '决策：PERMIT/DENY',
    reason_code             varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定决策原因编码',
    policy_version_ids_json json          DEFAULT NULL COMMENT '命中策略版本ID集合',
    grant_ids_json          json          DEFAULT NULL COMMENT '命中Grant ID集合',
    delegation_id           bigint        DEFAULT NULL COMMENT '命中委托主键',
    break_glass_access_id   bigint        DEFAULT NULL COMMENT '命中应急访问主键',
    obligation_summary_json json          DEFAULT NULL COMMENT '生效Obligation摘要',
    purpose                 varchar(500)  DEFAULT NULL COMMENT '访问用途',
    trace_id                varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '链路追踪ID',
    duration_ms             bigint        NOT NULL COMMENT '决策耗时毫秒',
    occurred_time           datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '决策发生时间',
    PRIMARY KEY (decision_id),
    KEY idx_authz_decision_subject
        (tenant_id, subject_type, subject_id, occurred_time),
    KEY idx_authz_decision_resource
        (resource_tenant_id, resource_id, occurred_time),
    KEY idx_authz_decision_result (tenant_id, decision, reason_code, occurred_time),
    KEY idx_authz_decision_trace (trace_id),
    CONSTRAINT ck_authz_decision_owner CHECK (tenant_id = resource_tenant_id),
    CONSTRAINT ck_authz_decision_subject CHECK (subject_type IN ('HUMAN', 'SERVICE', 'AGENT')),
    CONSTRAINT ck_authz_decision_result CHECK (decision IN ('PERMIT', 'DENY')),
    CONSTRAINT ck_authz_decision_duration CHECK (duration_ms >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '授权决策追加审计日志';

-- 验证：第一版空库执行后预期返回11
SELECT COUNT(*) AS platform_auth_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'authz_resource',
    'authz_resource_relation',
    'authz_policy_group',
    'authz_policy_group_version',
    'authz_policy_runtime_rule',
    'authz_policy_runtime_subject',
    'authz_policy_runtime_resource',
    'authz_policy_operation_log',
    'authz_grant',
    'authz_grant_scope',
    'authz_decision_log'
  );

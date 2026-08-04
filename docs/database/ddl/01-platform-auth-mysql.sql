-- ============================================================================
-- 政务数据中台 platform-auth 第一版核心 DDL
-- 数据库：MySQL 8.0
-- 状态：第一版目标结构；已同步 platform-auth Java 实现与空库运行基线
-- 范围：Human Resource、不可变 Policy、AuthorizationGrant、Obligation 与授权审计
-- 说明：SERVICE、AttributeSchema、Delegation、BreakGlass 的后续阶段定义以注释附录保留，不执行建表
-- 约束：不建立物理外键；引用完整性由应用服务、事务、索引、对账任务和集成测试保证
-- ============================================================================

SET NAMES utf8mb4;

/* 后续阶段：SERVICE 主体和属性 Schema，第一版不建表。
CREATE TABLE platform_service_principal
(
    service_id       bigint       NOT NULL COMMENT 'SERVICE主体主键，雪花ID',
    tenant_id        varchar(20)  NOT NULL COMMENT '地区租户编号',
    service_code     varchar(100) NOT NULL COMMENT '租户内稳定服务主体编码',
    service_name     varchar(200) NOT NULL COMMENT '服务主体名称',
    owner_dept_id    bigint       NOT NULL COMMENT '责任部门ID',
    client_id        varchar(64)  NOT NULL COMMENT '关联认证客户端ID',
    secret_ref       varchar(200) DEFAULT NULL COMMENT '客户端凭据SecretRef',
    purpose_scope    varchar(500) NOT NULL COMMENT '允许使用目的范围说明',
    effective_time   datetime(3)  NOT NULL COMMENT '生效时间',
    expire_time      datetime(3)  DEFAULT NULL COMMENT '失效时间',
    status           varchar(32)  NOT NULL COMMENT '状态：ACTIVE/DISABLED/EXPIRED/ARCHIVED',
    lock_version     int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag         char(1)      NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept      bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by        bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by        bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (service_id),
    UNIQUE KEY uk_platform_service_tenant_id (tenant_id, service_id),
    UNIQUE KEY uk_platform_service_code (tenant_id, service_code),
    UNIQUE KEY uk_platform_service_client (tenant_id, client_id),
    KEY idx_platform_service_owner (tenant_id, owner_dept_id, status),
    KEY idx_platform_service_validity (tenant_id, status, effective_time, expire_time),
    CONSTRAINT ck_platform_service_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT ck_platform_service_validity
        CHECK (expire_time IS NULL OR expire_time > effective_time),
    CONSTRAINT ck_platform_service_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SERVICE主体身份';

CREATE TABLE authz_attribute_schema
(
    schema_id           bigint        NOT NULL COMMENT '属性Schema主键，雪花ID',
    scope_type          varchar(16)   NOT NULL COMMENT '作用域类型：GLOBAL/TENANT',
    scope_key           varchar(20)   NOT NULL COMMENT '作用域键：GLOBAL或真实租户编号',
    attribute_code      varchar(160)  NOT NULL COMMENT '带命名空间的稳定属性编码',
    namespace           varchar(100)  NOT NULL COMMENT '属性命名空间',
    applies_to          varchar(32)   NOT NULL COMMENT '适用对象：SUBJECT/RESOURCE/ENVIRONMENT',
    data_type           varchar(32)   NOT NULL COMMENT '数据类型：STRING/NUMBER/BOOLEAN/DATETIME/STRING_SET',
    allowed_values_json json          DEFAULT NULL COMMENT '允许值定义JSON',
    required_flag       tinyint(1)    NOT NULL DEFAULT 0 COMMENT '是否必需',
    overridable_flag    tinyint(1)    NOT NULL DEFAULT 0 COMMENT 'GLOBAL定义是否允许租户覆盖',
    description         varchar(1000) DEFAULT NULL COMMENT '属性说明',
    status              varchar(32)   NOT NULL COMMENT '状态：ACTIVE/DISABLED/ARCHIVED',
    lock_version        int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (schema_id),
    UNIQUE KEY uk_authz_attr_scope_code (scope_type, scope_key, attribute_code),
    KEY idx_authz_attr_lookup (applies_to, namespace, status),
    CONSTRAINT ck_authz_attr_scope
        CHECK ((scope_type = 'GLOBAL' AND scope_key = 'GLOBAL')
            OR (scope_type = 'TENANT' AND scope_key <> 'GLOBAL')),
    CONSTRAINT ck_authz_attr_applies
        CHECK (applies_to IN ('SUBJECT', 'RESOURCE', 'ENVIRONMENT')),
    CONSTRAINT ck_authz_attr_type
        CHECK (data_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'STRING_SET')),
    CONSTRAINT ck_authz_attr_status CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ABAC属性Schema注册表';
*/

CREATE TABLE authz_resource
(
    resource_id          bigint        NOT NULL COMMENT '资源主键，雪花ID',
    tenant_id            varchar(20)   NOT NULL COMMENT '地区租户编号',
    resource_code        varchar(200)  NOT NULL COMMENT '租户内稳定资源编码',
    resource_name        varchar(200)  NOT NULL COMMENT '资源名称',
    resource_type        varchar(120)  NOT NULL COMMENT '资源类型编码',
    parent_id            bigint        DEFAULT NULL COMMENT '父资源主键',
    source_module        varchar(100)  NOT NULL COMMENT '来源模块',
    business_id          varchar(100)  NOT NULL COMMENT '来源模块稳定业务ID',
    owner_dept_id        bigint        DEFAULT NULL COMMENT '权属部门ID',
    region_code          varchar(32)   DEFAULT NULL COMMENT '行政区划编码',
    classification_level varchar(64)   DEFAULT NULL COMMENT '数据分类级别',
    security_level       int           DEFAULT NULL COMMENT '安全密级',
    attributes_json      json          DEFAULT NULL COMMENT '已注册扩展授权属性JSON',
    inherit_policy       tinyint(1)    NOT NULL DEFAULT 1 COMMENT '是否继承父资源策略',
    status               varchar(32)   NOT NULL COMMENT '状态：ACTIVE/DISABLED/ARCHIVED',
    lock_version         int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag             char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept          bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by            bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by            bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time          datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (resource_id),
    UNIQUE KEY uk_authz_resource_tenant_id (tenant_id, resource_id),
    UNIQUE KEY uk_authz_resource_code (tenant_id, resource_code),
    UNIQUE KEY uk_authz_resource_business (tenant_id, source_module, resource_type, business_id),
    KEY idx_authz_resource_parent (tenant_id, parent_id),
    KEY idx_authz_resource_type_status (tenant_id, resource_type, status),
    KEY idx_authz_resource_owner (tenant_id, owner_dept_id, status),
    CONSTRAINT ck_authz_resource_status CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_authz_resource_security CHECK (security_level IS NULL OR security_level >= 0),
    CONSTRAINT ck_authz_resource_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '统一可授权资源投影';

CREATE TABLE authz_resource_relation
(
    relation_id        bigint       NOT NULL COMMENT '资源关系主键，雪花ID',
    tenant_id          varchar(20)  NOT NULL COMMENT '地区租户编号',
    source_resource_id bigint       NOT NULL COMMENT '源资源主键',
    target_resource_id bigint       NOT NULL COMMENT '目标资源主键',
    relation_type      varchar(64)  NOT NULL COMMENT '稳定关系类型编码',
    create_dept        bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by          bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (relation_id),
    UNIQUE KEY uk_authz_relation_tenant_id (tenant_id, relation_id),
    UNIQUE KEY uk_authz_relation_edge
        (tenant_id, source_resource_id, target_resource_id, relation_type),
    KEY idx_authz_relation_source (tenant_id, source_resource_id, relation_type),
    KEY idx_authz_relation_target (tenant_id, target_resource_id, relation_type),
    CONSTRAINT ck_authz_relation_self CHECK (source_resource_id <> target_resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '授权资源非层级关系';

CREATE TABLE authz_policy
(
    policy_id         bigint       NOT NULL COMMENT '策略主键，雪花ID',
    tenant_id         varchar(20)  NOT NULL COMMENT '地区租户编号',
    policy_code       varchar(100) NOT NULL COMMENT '租户内稳定策略编码',
    policy_name       varchar(200) NOT NULL COMMENT '策略名称',
    current_version_id bigint      DEFAULT NULL COMMENT '当前发布策略版本主键',
    risk_level        varchar(16)  NOT NULL DEFAULT 'LOW' COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
    status            varchar(32)  NOT NULL COMMENT '状态：DRAFT/REVIEWING/PUBLISHED/DISABLED/ARCHIVED',
    lock_version      int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag          char(1)      NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept       bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by         bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_authz_policy_tenant_id (tenant_id, policy_id),
    UNIQUE KEY uk_authz_policy_code (tenant_id, policy_code),
    KEY idx_authz_policy_status (tenant_id, status, risk_level),
    CONSTRAINT ck_authz_policy_status
        CHECK (status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_authz_policy_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_authz_policy_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ABAC策略聚合根';

CREATE TABLE authz_policy_version
(
    policy_version_id bigint       NOT NULL COMMENT '策略版本主键，雪花ID',
    tenant_id         varchar(20)  NOT NULL COMMENT '地区租户编号',
    policy_id         bigint       NOT NULL COMMENT '策略主键',
    version_no        int          NOT NULL COMMENT '递增版本号',
    effect            varchar(16)  NOT NULL COMMENT '策略效果：PERMIT/DENY',
    condition_json    json         NOT NULL COMMENT '受限ABAC条件DSL',
    obligation_json   json         DEFAULT NULL COMMENT '结构化附加约束',
    priority          int          NOT NULL DEFAULT 0 COMMENT '策略优先级，值越大优先级越高',
    version_status    varchar(32)  NOT NULL COMMENT '版本状态：DRAFT/REVIEWING/PUBLISHED/DISABLED/ARCHIVED',
    content_hash      char(64)     NOT NULL COMMENT '版本及全部子项规范化SHA-256',
    created_by        bigint       DEFAULT NULL COMMENT '版本创建人ID',
    created_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本创建时间',
    published_by      bigint       DEFAULT NULL COMMENT '版本发布人ID',
    published_time    datetime(3)  DEFAULT NULL COMMENT '版本发布时间',
    PRIMARY KEY (policy_version_id),
    UNIQUE KEY uk_authz_policy_ver_tenant_id (tenant_id, policy_version_id),
    UNIQUE KEY uk_authz_policy_ver_owner (tenant_id, policy_id, policy_version_id),
    UNIQUE KEY uk_authz_policy_ver_no (tenant_id, policy_id, version_no),
    UNIQUE KEY uk_authz_policy_ver_hash (tenant_id, policy_id, content_hash),
    KEY idx_authz_policy_ver_status (tenant_id, policy_id, version_status),
    CONSTRAINT ck_authz_policy_ver_no CHECK (version_no > 0),
    CONSTRAINT ck_authz_policy_ver_effect CHECK (effect IN ('PERMIT', 'DENY')),
    CONSTRAINT ck_authz_policy_ver_status
        CHECK (version_status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_authz_policy_ver_publish
        CHECK (version_status <> 'PUBLISHED' OR published_time IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可变ABAC策略版本';

CREATE TABLE authz_policy_subject
(
    binding_id       bigint       NOT NULL COMMENT '策略主体绑定主键，雪花ID',
    tenant_id        varchar(20)  NOT NULL COMMENT '地区租户编号',
    policy_version_id bigint      NOT NULL COMMENT '策略版本主键',
    subject_type     varchar(32)  NOT NULL COMMENT '主体类型：ANY/HUMAN/ROLE/DEPARTMENT/SERVICE/AGENT',
    subject_id       varchar(100) NOT NULL COMMENT '主体稳定ID，ANY时固定为*',
    create_by        bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_authz_policy_subject_tenant_id (tenant_id, binding_id),
    UNIQUE KEY uk_authz_policy_subject
        (tenant_id, policy_version_id, subject_type, subject_id),
    KEY idx_authz_policy_subject_lookup
        (tenant_id, subject_type, subject_id, policy_version_id),
    CONSTRAINT ck_authz_policy_subject_type
        CHECK (subject_type IN ('ANY', 'HUMAN', 'ROLE', 'DEPARTMENT', 'SERVICE', 'AGENT')),
    CONSTRAINT ck_authz_policy_subject_id
        CHECK ((subject_type = 'ANY' AND subject_id = '*')
            OR (subject_type <> 'ANY' AND subject_id <> '*'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略版本主体绑定';

CREATE TABLE authz_policy_resource
(
    binding_id        bigint       NOT NULL COMMENT '策略资源绑定主键，雪花ID',
    tenant_id         varchar(20)  NOT NULL COMMENT '地区租户编号',
    policy_version_id bigint       NOT NULL COMMENT '策略版本主键',
    resource_id       bigint       DEFAULT NULL COMMENT '资源主键，按具体资源绑定时填写',
    resource_type     varchar(120) DEFAULT NULL COMMENT '资源类型，按类型绑定时填写',
    resource_selector varchar(220) GENERATED ALWAYS AS
        (CASE WHEN resource_id IS NOT NULL
            THEN CONCAT('ID:', CAST(resource_id AS CHAR))
            ELSE CONCAT('TYPE:', resource_type) END) STORED COMMENT '资源选择器唯一键',
    include_children  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否包含后代资源',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_authz_policy_resource_tenant_id (tenant_id, binding_id),
    UNIQUE KEY uk_authz_policy_resource_selector
        (tenant_id, policy_version_id, resource_selector),
    KEY idx_authz_policy_resource_id (tenant_id, resource_id, policy_version_id),
    KEY idx_authz_policy_resource_type (tenant_id, resource_type, policy_version_id),
    CONSTRAINT ck_authz_policy_resource_target
        CHECK ((resource_id IS NOT NULL AND resource_type IS NULL)
            OR (resource_id IS NULL AND resource_type IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略版本资源绑定';

CREATE TABLE authz_policy_action
(
    binding_id        bigint       NOT NULL COMMENT '策略动作绑定主键，雪花ID',
    tenant_id         varchar(20)  NOT NULL COMMENT '地区租户编号',
    policy_version_id bigint       NOT NULL COMMENT '策略版本主键',
    action_code       varchar(100) NOT NULL COMMENT '稳定授权动作编码',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_authz_policy_action_tenant_id (tenant_id, binding_id),
    UNIQUE KEY uk_authz_policy_action (tenant_id, policy_version_id, action_code),
    KEY idx_authz_policy_action_lookup (tenant_id, action_code, policy_version_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略版本动作绑定';

CREATE TABLE authz_grant
(
    grant_id          bigint        NOT NULL COMMENT 'AuthorizationGrant主键，雪花ID',
    tenant_id         varchar(20)   NOT NULL COMMENT '地区租户编号',
    grant_no          varchar(100)  NOT NULL COMMENT '租户内稳定授权编号',
    source_type       varchar(32)   NOT NULL COMMENT '来源类型：SHARING/ADMIN/SUBSCRIPTION/DELEGATION/SYSTEM',
    source_id         varchar(100)  NOT NULL COMMENT '来源业务稳定ID',
    grantor_type      varchar(32)   NOT NULL COMMENT '授权方类型：HUMAN/SERVICE/DEPARTMENT/SYSTEM',
    grantor_id        varchar(100)  NOT NULL COMMENT '授权方稳定ID',
    grantee_type      varchar(32)   NOT NULL COMMENT '被授权方类型：HUMAN/ROLE/DEPARTMENT/SERVICE/AGENT',
    grantee_id        varchar(100)  NOT NULL COMMENT '被授权方稳定ID',
    purpose           varchar(500)  NOT NULL COMMENT '授权用途',
    effective_time    datetime(3)   NOT NULL COMMENT '生效时间',
    expire_time       datetime(3)   DEFAULT NULL COMMENT '失效时间',
    condition_json    json          DEFAULT NULL COMMENT '附加受限ABAC条件',
    status            varchar(32)   NOT NULL COMMENT '状态：DRAFT/PENDING/ACTIVE/EXPIRED/REVOKED/ARCHIVED',
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
    UNIQUE KEY uk_authz_grant_tenant_id (tenant_id, grant_id),
    UNIQUE KEY uk_authz_grant_no (tenant_id, grant_no),
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
    tenant_id         varchar(20)  NOT NULL COMMENT '地区租户编号',
    grant_id          bigint       NOT NULL COMMENT 'AuthorizationGrant主键',
    resource_id       bigint       NOT NULL COMMENT '资源主键',
    action_code       varchar(100) NOT NULL COMMENT '稳定授权动作编码',
    include_children  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否包含后代资源',
    obligation_json   json         DEFAULT NULL COMMENT '结构化行列、脱敏、限量和导出约束',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_authz_grant_scope_tenant_id (tenant_id, scope_id),
    UNIQUE KEY uk_authz_grant_scope (tenant_id, grant_id, resource_id, action_code),
    KEY idx_authz_grant_scope_resource (tenant_id, resource_id, action_code, grant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Grant资源动作与Obligation范围';

/* 后续阶段：Agent Delegation 与 BreakGlass，第一版不建表。
CREATE TABLE authz_delegation
(
    delegation_id    bigint        NOT NULL COMMENT 'Agent委托主键，雪花ID',
    tenant_id        varchar(20)   NOT NULL COMMENT '地区租户编号',
    delegation_no    varchar(100)  NOT NULL COMMENT '租户内稳定委托编号',
    delegator_type   varchar(32)   NOT NULL COMMENT '委托方类型：HUMAN/SERVICE',
    delegator_id     varchar(100)  NOT NULL COMMENT '委托方稳定ID',
    agent_id         bigint        NOT NULL COMMENT 'Agent稳定ID，跨platform-mcp引用',
    agent_task_id    bigint        NOT NULL COMMENT 'Agent任务ID，跨platform-mcp引用',
    purpose          varchar(500)  NOT NULL COMMENT '委托用途',
    effective_time   datetime(3)   NOT NULL COMMENT '生效时间',
    expire_time      datetime(3)   NOT NULL COMMENT '失效时间',
    condition_json   json          DEFAULT NULL COMMENT '附加受限ABAC条件',
    source_grant_id  bigint        DEFAULT NULL COMMENT '来源Grant主键',
    status           varchar(32)   NOT NULL COMMENT '状态：DRAFT/PENDING/ACTIVE/EXPIRED/REVOKED/ARCHIVED',
    revoked_by       bigint        DEFAULT NULL COMMENT '撤销人ID',
    revoked_time     datetime(3)   DEFAULT NULL COMMENT '撤销时间',
    revoke_reason    varchar(1000) DEFAULT NULL COMMENT '撤销原因',
    lock_version     int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag         char(1)       NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept      bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by        bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by        bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (delegation_id),
    UNIQUE KEY uk_authz_delegation_tenant_id (tenant_id, delegation_id),
    UNIQUE KEY uk_authz_delegation_no (tenant_id, delegation_no),
    UNIQUE KEY uk_authz_delegation_task (tenant_id, agent_task_id),
    KEY idx_authz_delegation_agent (tenant_id, agent_id, status, expire_time),
    KEY idx_authz_delegation_delegator (tenant_id, delegator_type, delegator_id, status),
    KEY idx_authz_delegation_source_grant (tenant_id, source_grant_id),
    CONSTRAINT ck_authz_delegation_delegator CHECK (delegator_type IN ('HUMAN', 'SERVICE')),
    CONSTRAINT ck_authz_delegation_status
        CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED', 'ARCHIVED')),
    CONSTRAINT ck_authz_delegation_validity CHECK (expire_time > effective_time),
    CONSTRAINT ck_authz_delegation_revoked
        CHECK (status <> 'REVOKED' OR revoked_time IS NOT NULL),
    CONSTRAINT ck_authz_delegation_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'HUMAN或SERVICE对Agent的有限委托';

CREATE TABLE authz_delegation_scope
(
    scope_id          bigint       NOT NULL COMMENT '委托范围主键，雪花ID',
    tenant_id         varchar(20)  NOT NULL COMMENT '地区租户编号',
    delegation_id     bigint       NOT NULL COMMENT '委托主键',
    resource_id       bigint       NOT NULL COMMENT '资源主键',
    action_code       varchar(100) NOT NULL COMMENT '稳定授权动作编码',
    include_children  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否包含后代资源',
    obligation_json   json         DEFAULT NULL COMMENT '委托附加约束',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_authz_deleg_scope_tenant_id (tenant_id, scope_id),
    UNIQUE KEY uk_authz_delegation_scope (tenant_id, delegation_id, resource_id, action_code),
    KEY idx_authz_deleg_scope_delegation (tenant_id, delegation_id),
    KEY idx_authz_deleg_scope_resource (tenant_id, resource_id, action_code, delegation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent委托资源动作范围';

CREATE TABLE authz_break_glass_access
(
    access_id           bigint        NOT NULL COMMENT '应急访问主键，雪花ID',
    tenant_id           varchar(20)   NOT NULL COMMENT '记录归属租户，必须等于source_tenant_id',
    request_no          varchar(100)  NOT NULL COMMENT '应急访问申请编号',
    requester_id        bigint        NOT NULL COMMENT '申请人用户ID',
    source_tenant_id    varchar(20)   NOT NULL COMMENT '申请方租户编号',
    target_tenant_id    varchar(20)   NOT NULL COMMENT '目标租户编号',
    reason              varchar(1000) NOT NULL COMMENT '应急访问原因',
    readonly_flag       tinyint(1)    NOT NULL DEFAULT 1 COMMENT '是否只读，默认只读',
    workflow_instance_id bigint       NOT NULL COMMENT '审批流程实例ID',
    effective_time      datetime(3)   NOT NULL COMMENT '生效时间',
    expire_time         datetime(3)   NOT NULL COMMENT '失效时间',
    status              varchar(32)   NOT NULL COMMENT '状态：PENDING/APPROVED/ACTIVE/EXPIRED/REVOKED/REJECTED',
    approved_by         bigint        DEFAULT NULL COMMENT '最终批准人ID',
    approved_time       datetime(3)   DEFAULT NULL COMMENT '最终批准时间',
    revoked_by          bigint        DEFAULT NULL COMMENT '撤销人ID',
    revoked_time        datetime(3)   DEFAULT NULL COMMENT '撤销时间',
    lock_version        int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_dept         bigint        DEFAULT NULL COMMENT '创建部门ID',
    create_by           bigint        DEFAULT NULL COMMENT '创建人ID',
    create_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           bigint        DEFAULT NULL COMMENT '更新人ID',
    update_time         datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (access_id),
    UNIQUE KEY uk_authz_breakglass_tenant_id (tenant_id, access_id),
    UNIQUE KEY uk_authz_breakglass_target (tenant_id, access_id, target_tenant_id),
    UNIQUE KEY uk_authz_breakglass_no (source_tenant_id, request_no),
    UNIQUE KEY uk_authz_breakglass_workflow (workflow_instance_id),
    KEY idx_authz_breakglass_target (target_tenant_id, status, effective_time, expire_time),
    KEY idx_authz_breakglass_requester (source_tenant_id, requester_id, create_time),
    CONSTRAINT ck_authz_breakglass_owner CHECK (tenant_id = source_tenant_id),
    CONSTRAINT ck_authz_breakglass_cross_tenant CHECK (source_tenant_id <> target_tenant_id),
    CONSTRAINT ck_authz_breakglass_validity CHECK (expire_time > effective_time),
    CONSTRAINT ck_authz_breakglass_status
        CHECK (status IN ('PENDING', 'APPROVED', 'ACTIVE', 'EXPIRED', 'REVOKED', 'REJECTED')),
    CONSTRAINT ck_authz_breakglass_approval
        CHECK (status NOT IN ('APPROVED', 'ACTIVE') OR approved_time IS NOT NULL),
    CONSTRAINT ck_authz_breakglass_revoked
        CHECK (status <> 'REVOKED' OR revoked_time IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '跨租户应急访问审批记录';

CREATE TABLE authz_break_glass_scope
(
    scope_id          bigint       NOT NULL COMMENT '应急访问范围主键，雪花ID',
    tenant_id         varchar(20)  NOT NULL COMMENT '申请方租户编号',
    access_id         bigint       NOT NULL COMMENT '应急访问主键',
    target_tenant_id  varchar(20)  NOT NULL COMMENT '目标租户编号',
    resource_id       bigint       NOT NULL COMMENT '目标租户资源主键',
    action_code       varchar(100) NOT NULL COMMENT '稳定授权动作编码',
    obligation_json   json         DEFAULT NULL COMMENT '附加只读、字段、脱敏和限量约束',
    create_by         bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_authz_break_scope_tenant_id (tenant_id, scope_id),
    UNIQUE KEY uk_authz_break_scope (tenant_id, access_id, resource_id, action_code),
    KEY idx_authz_break_scope_access (tenant_id, access_id),
    KEY idx_authz_break_scope_target (target_tenant_id, resource_id, action_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '跨租户应急访问资源动作范围';
*/

CREATE TABLE authz_decision_log
(
    decision_id             bigint        NOT NULL COMMENT '授权决策日志主键，雪花ID',
    tenant_id               varchar(20)   NOT NULL COMMENT '审计归属租户，通常为资源租户',
    subject_tenant_id       varchar(20)   NOT NULL COMMENT '主体租户编号',
    subject_type            varchar(32)   NOT NULL COMMENT '主体类型：HUMAN/SERVICE/AGENT',
    subject_id              varchar(100)  NOT NULL COMMENT '主体稳定ID',
    subject_snapshot_json   json          NOT NULL COMMENT '最小主体属性快照',
    resource_tenant_id      varchar(20)   NOT NULL COMMENT '资源租户编号',
    resource_id             bigint        DEFAULT NULL COMMENT '资源主键',
    resource_type_snapshot  varchar(120)  NOT NULL COMMENT '资源类型快照',
    resource_code_snapshot  varchar(200)  NOT NULL COMMENT '资源编码快照',
    action_code             varchar(100)  NOT NULL COMMENT '授权动作编码',
    decision                varchar(16)   NOT NULL COMMENT '决策：PERMIT/DENY',
    reason_code             varchar(100)  NOT NULL COMMENT '稳定决策原因编码',
    policy_version_ids_json json          DEFAULT NULL COMMENT '命中策略版本ID集合',
    grant_ids_json          json          DEFAULT NULL COMMENT '命中Grant ID集合',
    delegation_id           bigint        DEFAULT NULL COMMENT '命中委托主键',
    break_glass_access_id   bigint        DEFAULT NULL COMMENT '命中应急访问主键',
    obligation_summary_json json          DEFAULT NULL COMMENT '生效Obligation摘要',
    purpose                 varchar(500)  DEFAULT NULL COMMENT '访问用途',
    trace_id                varchar(64)   NOT NULL COMMENT '链路追踪ID',
    duration_ms             bigint        NOT NULL COMMENT '决策耗时毫秒',
    occurred_time           datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '决策发生时间',
    PRIMARY KEY (decision_id),
    UNIQUE KEY uk_authz_decision_tenant_id (tenant_id, decision_id),
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

-- 验证：第一版空库执行后预期返回10
SELECT COUNT(*) AS platform_auth_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'authz_resource',
    'authz_resource_relation',
    'authz_policy',
    'authz_policy_version',
    'authz_policy_subject',
    'authz_policy_resource',
    'authz_policy_action',
    'authz_grant',
    'authz_grant_scope',
    'authz_decision_log'
  );

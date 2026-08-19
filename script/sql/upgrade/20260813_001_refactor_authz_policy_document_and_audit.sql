-- 策略版本完整文档、运行时索引及操作日志重构。
-- 执行前必须备份 platform 控制库；仅支持 MySQL 8.0。

ALTER TABLE authz_policy_version
    ADD COLUMN policy_document_json json NULL COMMENT '完整不可变策略文档，关联表可由此重建'
        AFTER effect;

UPDATE authz_policy_version version
SET version.policy_document_json = JSON_OBJECT(
    'policyName', version.policy_name_snapshot,
    'effect', version.effect,
    'riskLevel', version.risk_level,
    'priority', version.priority,
    'subjects', COALESCE((
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
            'subjectType', subject.subject_type,
            'subjectId', subject.subject_id
        ))
        FROM authz_policy_subject subject
        WHERE subject.tenant_id = version.tenant_id
          AND subject.policy_version_id = version.policy_version_id
    ), JSON_ARRAY()),
    'resources', COALESCE((
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
            'resourceId', resource.resource_id,
            'resourceType', resource.resource_type,
            'includeChildren', IF(resource.include_children = 1,
                JSON_EXTRACT('true', '$'), JSON_EXTRACT('false', '$'))
        ))
        FROM authz_policy_resource resource
        WHERE resource.tenant_id = version.tenant_id
          AND resource.policy_version_id = version.policy_version_id
    ), JSON_ARRAY()),
    'actions', COALESCE((
        SELECT JSON_ARRAYAGG(action.action_code)
        FROM authz_policy_action action
        WHERE action.tenant_id = version.tenant_id
          AND action.policy_version_id = version.policy_version_id
    ), JSON_ARRAY()),
    'condition', version.condition_json,
    'obligation', version.obligation_json
);

ALTER TABLE authz_policy_version
    MODIFY COLUMN policy_document_json json NOT NULL COMMENT '完整不可变策略文档，关联表可由此重建';

UPDATE authz_policy
SET status = CASE status
    WHEN 'PUBLISHED' THEN 'ACTIVE'
    WHEN 'REVIEWING' THEN 'DRAFT'
    WHEN 'ARCHIVED' THEN 'DISABLED'
    ELSE status
END;

UPDATE authz_policy_version
SET version_status = CASE version_status
    WHEN 'REVIEWING' THEN 'DRAFT'
    WHEN 'DISABLED' THEN 'PUBLISHED'
    WHEN 'ARCHIVED' THEN 'SUPERSEDED'
    ELSE version_status
END;

ALTER TABLE authz_policy
    DROP CHECK ck_authz_policy_status,
    ADD CONSTRAINT ck_authz_policy_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED'));

ALTER TABLE authz_policy_version
    DROP CHECK ck_authz_policy_ver_status,
    ADD CONSTRAINT ck_authz_policy_ver_status
        CHECK (version_status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED'));

CREATE TABLE authz_policy_operation_log
(
    operation_log_id      bigint       NOT NULL COMMENT '策略操作日志主键，雪花ID',
    tenant_id             varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_id             bigint       NOT NULL COMMENT '策略主键',
    policy_group_code     varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '策略模板组编码快照',
    policy_version_id     bigint       DEFAULT NULL COMMENT '本次操作关联的策略版本主键',
    operation_type        varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作类型',
    from_status           varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '操作前聚合状态',
    to_status             varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '操作后聚合状态',
    operation_reason      varchar(500) DEFAULT NULL COMMENT '操作原因',
    operation_detail_json json         DEFAULT NULL COMMENT '变更字段、版本号等结构化上下文',
    operator_type         varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作主体类型',
    operator_id           varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作主体稳定ID',
    trace_id              varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '链路追踪ID',
    occurred_time         datetime(3)  NOT NULL COMMENT '操作发生时间',
    PRIMARY KEY (operation_log_id),
    KEY idx_authz_policy_operation_policy (tenant_id, policy_id, occurred_time),
    KEY idx_authz_policy_operation_version (tenant_id, policy_version_id, occurred_time),
    KEY idx_authz_policy_operation_trace (trace_id),
    CONSTRAINT ck_authz_policy_operation_type CHECK (operation_type IN
        ('CREATE_DRAFT', 'UPDATE_DRAFT', 'CREATE_VERSION', 'PUBLISH', 'DISABLE', 'DELETE_DRAFT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略生命周期追加操作日志';

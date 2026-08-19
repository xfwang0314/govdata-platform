-- module: platform-auth
-- depends-on: 20260811_003_add_authz_policy_group.sql
-- purpose: 持久化策略模板组配置，支持模板详情和草稿原样编辑
-- expected-rows: 0（仅创建新表；历史仅有组编码的策略不会伪造模板配置）
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 确认 authz_policy_group 表不存在且 003 已执行
-- post-check: 检查表、租户内组编码唯一索引和模板查询索引已创建
-- rollback-or-forward-fix: 应用回退后可删除空表；存在数据时使用前向修复保留审计快照

CREATE TABLE authz_policy_group
(
    policy_group_id       bigint       NOT NULL COMMENT '策略模板组主键，雪花ID',
    tenant_id             varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区租户编号',
    policy_group_code     varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后端生成的稳定策略组编码',
    policy_group_name     varchar(120) NOT NULL COMMENT '策略组名称',
    template_code         varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '策略模板稳定编码',
    template_command_json json         NOT NULL COMMENT '模板配置快照JSON，用于详情和草稿回显',
    lock_version          int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag              char(1)      NOT NULL DEFAULT '0' COMMENT '删除标志：0存在，1删除',
    create_dept           bigint       DEFAULT NULL COMMENT '创建部门ID',
    create_by             bigint       DEFAULT NULL COMMENT '创建人ID',
    create_time           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by             bigint       DEFAULT NULL COMMENT '更新人ID',
    update_time           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (policy_group_id),
    UNIQUE KEY uk_authz_policy_group_code (tenant_id, policy_group_code),
    KEY idx_authz_policy_group_template (tenant_id, template_code, update_time),
    CONSTRAINT ck_authz_policy_group_del_flag CHECK (del_flag IN ('0', '1'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略模板组配置与回显快照';

-- 1.ontology_definition 本体定义表

CREATE TABLE ontology_definition (
                                     ontology_id        bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '本体ID',
                                     tenant_id          varchar(20)     DEFAULT '000000' COMMENT '租户编号',
                                     ontology_code      varchar(100)    NOT NULL COMMENT '本体编码',
                                     ontology_name      varchar(200)    NOT NULL COMMENT '本体名称',
                                     ontology_type      varchar(50)     NOT NULL COMMENT '本体类型（FOUNDATION基础本体 DOMAIN领域本体 APPLICATION应用本体）',
                                     description        varchar(1000)   DEFAULT NULL COMMENT '本体描述',
                                     status             char(1)         DEFAULT '0' COMMENT '状态（0正常 1停用）',
                                     del_flag           char(1)         DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
                                     create_by          bigint(20)      DEFAULT NULL COMMENT '创建人',
                                     create_time        datetime        DEFAULT NULL COMMENT '创建时间',
                                     update_by          bigint(20)      DEFAULT NULL COMMENT '修改人',
                                     update_time        datetime        DEFAULT NULL COMMENT '更新时间',
                                     remark             varchar(500)    DEFAULT NULL COMMENT '备注',
                                     PRIMARY KEY (ontology_id),
                                     UNIQUE KEY uk_ontology_code (tenant_id, ontology_code),
                                     KEY idx_type (ontology_type, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology本体定义表';

-- 2.ontology_version 本体版本表

CREATE TABLE ontology_version (
                                  version_id          bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '版本ID',
                                  ontology_id         bigint(20)      NOT NULL COMMENT '本体ID',
                                  version_no          varchar(50)     NOT NULL COMMENT '版本号，例如1.0.0',
                                  version_status      varchar(20)     DEFAULT 'DRAFT' COMMENT '版本状态（DRAFT草稿 REVIEW审核 RELEASE发布 DEPRECATED废弃）',
                                  parent_version_id   bigint(20)      DEFAULT NULL COMMENT '来源版本ID',
                                  change_summary      varchar(1000)   DEFAULT NULL COMMENT '版本变更说明',
                                  is_current          char(1)         DEFAULT '0' COMMENT '是否当前运行版本（0否 1是）',
                                  create_by           bigint(20)      DEFAULT NULL COMMENT '创建人',
                                  create_time         datetime        DEFAULT NULL COMMENT '创建时间',
                                  publish_by          bigint(20)      DEFAULT NULL COMMENT '发布人',
                                  publish_time        datetime        DEFAULT NULL COMMENT '发布时间',
                                  remark              varchar(500)    DEFAULT NULL COMMENT '备注',
                                  PRIMARY KEY (version_id),
                                  UNIQUE KEY uk_version (ontology_id, version_no),
                                  KEY idx_current (ontology_id, is_current),
                                  KEY idx_status (version_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology版本管理表';

-- 3. ontology_release 本体发布表

CREATE TABLE ontology_release (
                                  release_id          bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '发布ID',
                                  ontology_id         bigint(20)      NOT NULL COMMENT '本体ID',
                                  version_id          bigint(20)      NOT NULL COMMENT '版本ID',
                                  release_env         varchar(50)     DEFAULT 'PROD' COMMENT '发布环境（DEV开发 TEST测试 PROD生产）',
                                  release_status      varchar(20)     DEFAULT 'SUCCESS' COMMENT '发布状态（SUCCESS成功 FAIL失败）',
                                  release_by          bigint(20)      DEFAULT NULL COMMENT '发布人',
                                  release_time        datetime        DEFAULT NULL COMMENT '发布时间',
                                  rollback_version_id bigint(20)      DEFAULT NULL COMMENT '回滚版本ID',
                                  remark              varchar(500)    DEFAULT NULL COMMENT '备注',
                                  PRIMARY KEY (release_id),
                                  KEY idx_version (version_id),
                                  KEY idx_ontology (ontology_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology发布记录表';

-- 4. ontology_class 本体类定义表

CREATE TABLE ontology_class (
                                class_id             bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '类ID',
                                version_id           bigint(20)      NOT NULL COMMENT '所属版本ID',
                                class_code           varchar(100)    NOT NULL COMMENT '类编码',
                                class_name           varchar(100)    NOT NULL COMMENT '类名称',
                                parent_class_id      bigint(20)      DEFAULT 0 COMMENT '父类ID，支持本体继承',
                                is_abstract          char(1)         DEFAULT '0' COMMENT '是否抽象类（0否 1是）',
                                description          varchar(1000)   DEFAULT NULL COMMENT '业务语义描述',
                                extend_json          json            DEFAULT NULL COMMENT '扩展属性',
                                del_flag             char(1)         DEFAULT '0' COMMENT '删除标志',
                                create_by            bigint(20)      DEFAULT NULL COMMENT '创建人',
                                create_time          datetime        DEFAULT NULL COMMENT '创建时间',
                                update_by            bigint(20)      DEFAULT NULL COMMENT '修改人',
                                update_time          datetime        DEFAULT NULL COMMENT '更新时间',
                                remark               varchar(500)    DEFAULT NULL COMMENT '备注',
                                PRIMARY KEY (class_id),
                                UNIQUE KEY uk_class (version_id, class_code),
                                KEY idx_parent (parent_class_id),
                                KEY idx_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology类定义表';

-- 5. ontology_property 本体属性表

CREATE TABLE ontology_property (
                                   property_id          bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '属性ID',
                                   version_id           bigint(20)      NOT NULL COMMENT '所属版本ID',
                                   class_id             bigint(20)      NOT NULL COMMENT '所属类ID',
                                   property_code        varchar(100)    NOT NULL COMMENT '属性编码',
                                   property_name        varchar(100)    NOT NULL COMMENT '属性名称',
                                   data_type            varchar(50)     NOT NULL COMMENT '数据类型（string number date object array）',
                                   is_required          char(1)         DEFAULT '0' COMMENT '是否必填（0否 1是）',
                                   is_queryable         char(1)         DEFAULT '1' COMMENT '是否支持查询（0否 1是）',
                                   is_sensitive         char(1)         DEFAULT '0' COMMENT '是否敏感属性（0否 1是）',
                                   description          varchar(500)    DEFAULT NULL COMMENT '属性描述',
                                   extend_json          json            DEFAULT NULL COMMENT '扩展配置',
                                   del_flag             char(1)         DEFAULT '0' COMMENT '删除标志',
                                   create_by            bigint(20)      DEFAULT NULL COMMENT '创建人',
                                   create_time          datetime        DEFAULT NULL COMMENT '创建时间',
                                   update_by            bigint(20)      DEFAULT NULL COMMENT '修改人',
                                   update_time          datetime        DEFAULT NULL COMMENT '更新时间',
                                   remark               varchar(500)    DEFAULT NULL COMMENT '备注',
                                   PRIMARY KEY (property_id),
                                   UNIQUE KEY uk_property (version_id, class_id, property_code),
                                   KEY idx_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology属性定义表';

-- 6. ontology_relation 本体关系表

CREATE TABLE ontology_relation (
                                   relation_id          bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '关系ID',
                                   version_id           bigint(20)      NOT NULL COMMENT '所属Ontology版本ID',
                                   source_class_id      bigint(20)      NOT NULL COMMENT '源类ID',
                                   relation_code        varchar(100)    NOT NULL COMMENT '关系编码，例如owns、contains、dependsOn',
                                   relation_name        varchar(100)    NOT NULL COMMENT '关系名称，例如拥有、包含、依赖',
                                   inverse_code         varchar(100)    DEFAULT NULL COMMENT '反向关系编码，例如ownedBy、belongsTo',
                                   inverse_name         varchar(100)    DEFAULT NULL COMMENT '反向关系名称',
                                   target_class_id      bigint(20)      NOT NULL COMMENT '目标类ID',
                                   relation_type        varchar(50)     DEFAULT 'ASSOCIATION' COMMENT '关系类型（INHERIT继承 COMPOSITION组合 AGGREGATION聚合 ASSOCIATION关联 DEPENDENCY依赖 OWNERSHIP归属 REFERENCE引用 TRACEABILITY追溯）',
                                   cardinality          varchar(50)     DEFAULT NULL COMMENT '关系基数（ONE_ONE一对一 ONE_MANY一对多 MANY_ONE多对一 MANY_MANY多对多）',
                                   is_directed          char(1)         DEFAULT '1' COMMENT '是否有方向（0无方向 1有方向）',
                                   description          varchar(500)    DEFAULT NULL COMMENT '关系业务描述',
                                   extend_json          json            DEFAULT NULL COMMENT '扩展属性',
                                   del_flag             char(1)         DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
                                   create_by            bigint(20)      DEFAULT NULL COMMENT '创建人',
                                   create_time          datetime        DEFAULT NULL COMMENT '创建时间',
                                   update_by            bigint(20)      DEFAULT NULL COMMENT '修改人',
                                   update_time          datetime        DEFAULT NULL COMMENT '更新时间',
                                   remark               varchar(500)    DEFAULT NULL COMMENT '备注',
                                   PRIMARY KEY (relation_id),
                                   UNIQUE KEY uk_relation (version_id, source_class_id, relation_code, target_class_id),
                                   KEY idx_source_class (source_class_id),
                                   KEY idx_target_class (target_class_id),
                                   KEY idx_relation_type (relation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology关系定义表';

-- 7. ontology_capability 本体能力表

CREATE TABLE ontology_capability (
                                     capability_id          bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '能力ID',
                                     version_id             bigint(20)      NOT NULL COMMENT '所属Ontology版本ID',
                                     class_id               bigint(20)      NOT NULL COMMENT '所属本体类ID',
                                     action_code            varchar(100)    NOT NULL COMMENT '动作编码，例如query、create、execute',
                                     action_name            varchar(100)    NOT NULL COMMENT '动作名称',
                                     mcp_tool_id            bigint(20)      NOT NULL COMMENT '关联MCP工具ID',
                                     input_schema           json            DEFAULT NULL COMMENT '输入参数JSON Schema定义',
                                     output_schema          json            DEFAULT NULL COMMENT '输出结果JSON Schema定义',
                                     permission_code        varchar(100)    DEFAULT NULL COMMENT '权限编码，例如DATA_RESOURCE_QUERY、EXCHANGE_TASK_CREATE',
                                     risk_level             varchar(20)     DEFAULT 'LOW' COMMENT '风险等级（LOW低风险 MEDIUM中风险 HIGH高风险 CRITICAL极高风险）',
                                     execution_policy_code  varchar(100)    DEFAULT NULL COMMENT '执行策略编码，例如AUTO自动执行 CONFIRM人工确认 APPROVAL审批执行',
                                     description            varchar(500)    DEFAULT NULL COMMENT '能力描述',
                                     del_flag               char(1)         DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
                                     create_by              bigint(20)      DEFAULT NULL COMMENT '创建人',
                                     create_time            datetime        DEFAULT NULL COMMENT '创建时间',
                                     update_by              bigint(20)      DEFAULT NULL COMMENT '修改人',
                                     update_time            datetime        DEFAULT NULL COMMENT '更新时间',
                                     remark                 varchar(500)    DEFAULT NULL COMMENT '备注',
                                     PRIMARY KEY (capability_id),
                                     UNIQUE KEY uk_action (version_id, class_id, action_code),
                                     KEY idx_mcp (mcp_tool_id),
                                     KEY idx_permission (permission_code),
                                     KEY idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology能力定义表';

-- 8. ontology_dependency 本体依赖表

CREATE TABLE ontology_dependency (
                                     dependency_id        bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '依赖ID',
                                     ontology_id          bigint(20)      NOT NULL COMMENT '当前本体ID',
                                     version_id           bigint(20)      NOT NULL COMMENT '当前版本ID',
                                     depend_ontology_id   bigint(20)      NOT NULL COMMENT '依赖本体ID',
                                     depend_version_id    bigint(20)      DEFAULT NULL COMMENT '依赖版本ID',
                                     dependency_type      varchar(50)     DEFAULT 'IMPORT' COMMENT '依赖类型（IMPORT引用 EXTEND扩展）',
                                     version_constraint   varchar(50)     DEFAULT NULL COMMENT '版本约束，例如>=1.0',
                                     description          varchar(500)    DEFAULT NULL COMMENT '依赖说明',
                                     del_flag             char(1)         DEFAULT '0' COMMENT  '删除标志',
                                     create_by            bigint(20)      DEFAULT NULL COMMENT '创建人',
                                     create_time          datetime        DEFAULT NULL COMMENT '创建时间',
                                     update_by            bigint(20)      DEFAULT NULL COMMENT '修改人',
                                     update_time          datetime        DEFAULT NULL COMMENT '更新时间',
                                     remark               varchar(500)    DEFAULT NULL COMMENT '备注',
                                     PRIMARY KEY (dependency_id),
                                     UNIQUE KEY uk_dependency (version_id, depend_ontology_id, depend_version_id),
                                     KEY idx_ontology (ontology_id),
                                     KEY idx_depend (depend_ontology_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ontology本体依赖关系表';

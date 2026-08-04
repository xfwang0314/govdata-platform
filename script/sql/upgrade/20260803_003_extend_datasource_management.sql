-- ============================================================================
-- 模块：govdata-datasource
-- 前置：已执行 20260803_001_create_govdata_datasource.sql
-- 影响：补充数据源分类与安全等级字段；新增数据源菜单权限
-- 锁表风险：ALTER TABLE 获取元数据锁，生产需避开长事务
-- 回滚：先备份字段数据，再删除新增菜单和字段
-- ============================================================================

ALTER TABLE gd_datasource
    ADD COLUMN classification_level varchar(32) NOT NULL DEFAULT 'INTERNAL'
        COMMENT '数据分类级别' AFTER readonly_flag,
    ADD COLUMN security_level int NOT NULL DEFAULT 1
        COMMENT '安全等级1-5' AFTER classification_level,
    ADD CONSTRAINT ck_gd_datasource_security CHECK (security_level BETWEEN 1 AND 5);

INSERT INTO sys_menu VALUES
    (20000, '数据治理', 0, 7, 'govdata', NULL, '', 1, 0, 'M', '0', '0', '', 'database', 103, 1, NOW(), NULL, NULL, '政务数据中台业务目录'),
    (20100, '数据源管理', 20000, 1, 'datasource', 'govdata/datasource/index', '', 1, 0, 'C', '0', '0', 'govdata:datasource:list', 'connection', 103, 1, NOW(), NULL, NULL, '数据源管理菜单'),
    (20101, '数据源查询', 20100, 1, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:query', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20102, '数据源新增', 20100, 2, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:add', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20103, '数据源修改', 20100, 3, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20104, '数据源删除', 20100, 4, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20105, '连接测试', 20100, 5, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:test', '#', 103, 1, NOW(), NULL, NULL, ''),
    (20106, '测试记录', 20100, 6, '', '', '', 1, 0, 'F', '0', '0', 'govdata:datasource:test:list', '#', 103, 1, NOW(), NULL, NULL, '');

SELECT datasource_id, classification_level, security_level
FROM gd_datasource
ORDER BY datasource_id
LIMIT 10;

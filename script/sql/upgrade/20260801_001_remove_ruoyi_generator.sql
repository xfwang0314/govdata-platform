-- 模块：平台初始化
-- 前置版本：包含 RuoYi 在线代码生成器的 5.x 数据库
-- 影响：删除代码生成器菜单、角色菜单关系以及 gen_table/gen_table_column 表
-- 锁表风险：低；执行前仍需备份 gen_table 与 gen_table_column 中需要保留的生成配置
-- 回滚：无自动回滚；如需恢复，必须从执行前备份还原

START TRANSACTION;

DELETE FROM sys_role_menu
WHERE menu_id IN (115, 116, 1055, 1056, 1057, 1058, 1059, 1060);

DELETE FROM sys_menu
WHERE menu_id IN (1055, 1056, 1057, 1058, 1059, 1060, 116, 115);

COMMIT;

DROP TABLE IF EXISTS gen_table_column;
DROP TABLE IF EXISTS gen_table;

-- 验证：以下查询均应返回 0
SELECT COUNT(*) AS generator_menu_count
FROM sys_menu
WHERE menu_id IN (115, 116, 1055, 1056, 1057, 1058, 1059, 1060);

SELECT COUNT(*) AS generator_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('gen_table', 'gen_table_column');

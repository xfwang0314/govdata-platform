# SQL 规范迁移报告

- 状态：2026-08-04 基线迁移与 MySQL 8 空库验证完成
- 范围：平台自研 DDL、升级脚本与 Mapper SQL
- 规范：`docs/standards/mysql-sql.md` v2

## 本轮已完成

1. 建立 `script/sql/baseline/` 和人工维护的 `manifest.md`，形成唯一空库执行入口。
2. 上游 RuoYi、Workflow 与自研 platform-auth、platform-secret、govdata-datasource 分序管理。
3. 删除根目录旧建表脚本和 `docs/database/ddl/` 的重复可执行副本。
4. 自研基线移除物理外键、自增主键和整数显示宽度，统一 MySQL 8、雪花 ID、`datetime(3)`。
5. 授权资源、策略、Grant、凭据和数据源稳定编码改为后端生成，并建立前缀登记表。
6. 机器编码列使用 `ascii_bin`；Grant 字段统一为 `grant_code`，资源分类统一为
   `classification_code`。
7. SQL 静态门禁只豁免 `00_ruoyi.sql`、`10_workflow.sql` 两个上游兼容基线；所有自研
   Baseline 和新增 Upgrade 进入严格扫描。
8. 新增显式环境变量保护的 MySQL 8 Baseline 集成测试；已在随机隔离临时库按 manifest
   执行并校验核心表，测试结束后自动删除临时库。

## 仍需完成

1. 为已发布的历史 Upgrade 补建外部 checksum 清单；历史脚本保持不可修改。
2. 为授权、凭据、数据源核心查询补充 `EXPLAIN ANALYZE` 证据和“查询 → 索引”映射。
3. 明确决策日志、凭据访问日志和连接测试记录的日增量、在线保留期、归档与清理任务。
4. 已删除与正式领域词汇冲突的旧本体预留 DDL；`govdata-ontology` 模块开发前必须以
   `docs/database/04-standard-quality-ontology-tables.md` 为事实来源完成正式评审并新增 Baseline。

## 门禁原则

- 新可执行 SQL 只能进入 `script/sql/baseline/` 或 `script/sql/upgrade/`。
- `docs/` 只保留设计和说明，不复制可执行 DDL。
- 禁止把新文件加入遗留白名单来绕过测试。
- 真实数据库验证未完成前，不得把本轮 DDL 用于已有数据的环境。

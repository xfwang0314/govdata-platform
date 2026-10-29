# MySQL 8 Baseline 执行清单

- 状态：v1
- 数据库：MySQL 8
- 时区：Asia/Shanghai
- 字符集：utf8mb4

## 执行顺序

| 顺序 | 文件 | 所有者 | 类型 | 前置 |
|---|---|---|---|---|
| 00 | `00_ruoyi.sql` | RuoYi 上游兼容 | 脚手架基线 | 空库 |
| 10 | `10_workflow.sql` | RuoYi Workflow | 工作流基线 | 00 |
| 20 | `20_platform_auth.sql` | platform-auth | 平台授权 | 00 |
| 21 | `21_platform_secret.sql` | platform-secret | 凭据管理 | 00 |
| 30 | `30_govdata_datasource.sql` | govdata-datasource | 数据源 | 20、21 |
| 31 | `31_govdata_metadata.sql` | govdata-metadata | 版本化元数据目录 | 20、30 |
| 32 | `32_govdata_metadata_harvest.sql` | govdata-metadata-harvest | 元数据采集计划与运行 | 30、31 |
| 33 | `33_govdata_query.sql` | govdata-query | 查询访问追加审计 | 20、30、31 |

## 规则

1. 新装环境按上表顺序执行，不执行历史 `update/` 或 `upgrade/`。
2. `00_ruoyi.sql` 和 `10_workflow.sql` 保留上游兼容风格；自研 Baseline 必须遵循 SQL v2 规范。
3. Baseline 只表达当前最终结构；已部署环境通过 `script/sql/upgrade/` 前向升级。
4. 每个模块只修改自己拥有的 Baseline，不得跨模块复制建表语句。
5. 本清单是人工维护的执行 manifest，新增模块必须同步登记。
6. 本体、标准和质量目前只有评审设计，不在 Baseline 放置“预留表”；完成对应模块 DDL 评审后再登记。

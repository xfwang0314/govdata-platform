# ADR-0013：平台控制库仅支持 MySQL 8，移除 PG/Oracle/SQLServer 方言

- 状态：已采用
- 日期：2026-08-04

## 决策

平台控制库（govdata-platform 自身运行所需的业务库）统一使用 MySQL 8，
移除脚手架继承的 PostgreSQL、Oracle、SQL Server 方言支持及对应 SQL 脉冲文件。

具体移除范围：

| 类型 | 路径 | 说明 |
|------|------|------|
| SQL 文件（删除目录） | `script/sql/oracle/` | Oracle 全库初始化脚本 |
| SQL 文件（删除目录） | `script/sql/postgres/` | PostgreSQL 全库初始化脚本 |
| SQL 文件（删除目录） | `script/sql/sqlserver/` | SQL Server 全库初始化脚本 |
| 注释配置（可保留或删除） | `ruoyi-admin/pom.xml` L33–L51 | Oracle/PG/SQLServer 驱动注释块 |
| 注释配置（可保留或删除） | `ruoyi-admin/src/main/resources/application-dev.yml` L42–L57 | 多方言数据源注释配置 |
| 注释配置（可保留或删除） | `ruoyi-admin/src/main/resources/application-prod.yml` L45–L60 | 多方言数据源注释配置 |

## 理由

- 平台采用模块化单体，部署在政务单位环境；MySQL 8 在该场景已标准化，运维摩擦最小。
- `docs/standards/mysql-sql.md` 已明确：平台控制库只正式支持 MySQL 8，外部多数据源支持不等于控制库多方言支持。
- 维护 4 套方言 SQL 脉冲文件带来额外负担，且 PG/Oracle/SQLServer 版本实际未被任何环境使用。
- 减少一个冗余实例（避免将来 MySQL + PostgreSQL 双实例并跑）是当前阶段的主要驱动因素。

## 边界与约束

- 本决策**只影响平台控制库**，不影响 `govdata-datasource` / `govdata-query` 对外部
  PostgreSQL、Oracle、SQL Server 数据源的连接查询能力，后者属于业务功能，必须保留。
- 若未来 Agent 领域确实需要向量检索（pgvector）或图数据库能力，应**新建独立 PostgreSQL 实例**专用于该能力，
  而不是将平台控制库迁移至 PostgreSQL。该决策届时须另立 ADR。
- Quartz 调度基础设施库（`govdata_scheduler`）同样使用 MySQL，见 ADR-0012。

## 约束

- 禁止在 `script/sql/` 下新建 Oracle、PostgreSQL、SQL Server 子目录或对应初始化脚本。
- 所有新表和升级 SQL 只提供 MySQL 8 版本，遵循 `docs/standards/mysql-sql.md`。
- pom.xml 中 Oracle/PG/SQLServer 驱动注释块可随版本清理，不得取消注释后引入生产构建。

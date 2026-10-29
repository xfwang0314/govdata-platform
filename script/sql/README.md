# SQL 执行入口

平台控制库只支持 MySQL 8，SQL 的唯一事实来源是本目录。

| 目录 | 用途 | 何时执行 | 是否可修改 |
|---|---|---|---|
| `baseline/` | 空库当前最终结构与初始化数据 | 新装、可丢弃的空库 | 可随当前结构调整；必须同步 `baseline/manifest.md` |
| `upgrade/` | 已部署环境的前向变更 | 已有环境按 `upgrade/manifest.md` 顺序执行未应用脚本 | 已共享或执行后不可修改、重排、删除 |
| `update/` | RuoYi/WarmFlow 上游历史升级档案 | 仅按上游版本迁移说明需要时人工评估 | 不作为本项目常规升级入口 |

新空库只执行 `baseline/manifest.md`，已有数据库只执行 `upgrade/manifest.md` 中尚未应用的脚本，禁止把两套入口连续执行。
禁止在生产环境重新执行 Baseline；禁止把新项目 SQL 放入 `update/`；禁止以文件名排序替代 `upgrade/manifest.md` 的确认。

详细要求见 [`docs/standards/manual-database-upgrade.md`](../../docs/standards/manual-database-upgrade.md) 与
[`docs/standards/mysql-sql.md`](../../docs/standards/mysql-sql.md)。

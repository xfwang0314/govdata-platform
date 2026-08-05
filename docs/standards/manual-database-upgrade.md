# 手工数据库初始化与升级规范

- 状态：v2（强制，当前不使用 Flyway）
- 适用范围：MySQL 8 平台控制库 Baseline、升级、回填与生产修复
- 所有者：GovData Platform Team
- 最后更新：2026-08-04

## 1. 唯一事实来源

目标目录：

```text
script/sql/
├─ baseline/
│  ├─ 00_ruoyi.sql
│  ├─ 10_workflow.sql
│  ├─ 20_platform_auth.sql
│  ├─ 21_platform_secret.sql
│  ├─ 30_govdata_datasource.sql
│  └─ manifest.md
└─ upgrade/
   └─ YYYYMMDD_NNN_module_description.sql
```

- Baseline 是新装环境当前最终结构的唯一可执行事实来源。
- 每个模块维护自己的 Baseline 文件；manifest 固定顺序、所有者和前置依赖。
- Upgrade 是已部署环境变更的唯一来源。
- `docs/database/ddl/` 只保存表设计说明和 Baseline 链接，不保留重复可执行 DDL。
- 不自动生成 Baseline；模块负责人手工维护并接受评审。
- 旧根级 `ry_*.sql` 在下一阶段迁移完成后删除，不保留兼容副本。

## 2. 升级脚本命名与不可变性

- 文件名使用 `YYYYMMDD_NNN_module_description.sql`，同一天序号全仓唯一且递增。
- 已进入共享分支或在任一环境执行的 Upgrade 禁止修改、重排和删除。
- 已发布脚本有问题时新增前向修复脚本。
- 不用大量 `IF EXISTS/IF NOT EXISTS` 伪装幂等；结构不符合预期应失败并人工确认。
- Baseline 同步最终结构，但不复制历史迁移过程。

## 3. 文件头部元数据

每个 Upgrade 必须在文件头说明：

```sql
-- module: govdata-datasource
-- depends-on: 20260803_003_extend_datasource_management.sql
-- purpose: ...
-- expected-rows: ...
-- lock-risk: NONE | LOW | MEDIUM | HIGH
-- downtime-required: YES | NO
-- pre-check: ...
-- post-check: ...
-- rollback-or-forward-fix: ...
```

- 不确定的行数写估算区间，不能省略。
- 高锁风险、不可逆数据变化和停机变更必须单独审批。
- SQL 文件不得包含环境账号、密码、主机地址、真实数据或本地配置。

## 4. 执行记录

- 执行前备份并确认恢复方式。
- 校验脚本 checksum；checksum 与登记值不一致时禁止执行。
- 登记脚本名、checksum、环境、执行人、开始/结束时间、结果和验证证据。
- 按 manifest/发布清单顺序执行，不允许跳过中间脚本。
- MySQL DDL 会隐式提交，不得假定一个升级文件处于同一事务。
- 失败优先前向修复；危险回滚必须审批。

## 5. 兼容性迁移

新增非空字段采用：

1. 新增可空字段。
2. 发布兼容读写代码。
3. 按主键范围分批回填。
4. 验证 NULL、行数和业务一致性。
5. 增加默认值/NOT NULL/CHECK。
6. 切换应用使用新字段。
7. 后续版本停止兼容并清理旧字段。

字段重命名、类型收窄、拆表和删除使用“新增 → 兼容/双写 → 回填 → 校验 → 切读 → 停写 → 后续删除”。
禁止一步式破坏变更。

## 6. 索引与大表 DDL

- 变更前获取表行数、容量、写入量、关键查询和 MySQL 版本能力。
- 明确 `ALGORITHM`、`LOCK`、预计时间、磁盘临时空间和执行窗口。
- 不能因为 MySQL 8 支持部分在线 DDL 就假设所有 ALTER 无锁。
- 新索引必须有查询与 EXPLAIN ANALYZE 证据；删除索引必须检查慢 SQL和重复/左前缀覆盖。
- 大表变更需要灰度、监控、取消条件和应急方案。

## 7. 数据回填与清理

- DDL 与大批量数据迁移分离成不同脚本/任务。
- 回填按稳定主键范围推进，单批默认不超过 5000 行并记录进度。
- 每批独立提交、限速并监控锁等待、复制延迟和错误率。
- 回填必须可重入，但不得通过吞掉异常伪装成功。
- 生产修复先使用相同 WHERE 执行 SELECT/COUNT 并保留证据。
- 大批量 DELETE 由所属模块清理任务执行，不在发布脚本中一次删除全量。

## 8. 回滚

- 只有能安全恢复结构和数据的变更才提供直接回滚脚本。
- 涉及数据丢失、密钥轮换、外部副作用和不可逆状态变化时优先前向修复。
- 回滚不能恢复的数据必须在上线审批前明确说明。
- 删除字段前必须完成备份、停止读写、保留周期和恢复演练。

## 9. CI 与验收

- 空 MySQL 8 按 manifest 执行全部 Baseline。
- 从支持的最小版本按顺序执行 Upgrade。
- 校验脚本命名、头部元数据、checksum 和不可变性。
- 验证 Entity、Mapper 与最终 Schema 的主键、类型、租户、逻辑删除、乐观锁和时间精度。
- MySQL 特性测试必须使用真实 MySQL 8，禁止 H2 代替。
- 发布清单必须列出待执行脚本、顺序、预计时间、验证 SQL 和负责人。

所有 DDL/DML 仍必须遵循 [MySQL 8 与平台控制库 SQL 规范](mysql-sql.md)。

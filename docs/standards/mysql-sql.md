# MySQL 8 与平台控制库 SQL 规范

- 状态：v2（强制）
- 适用范围：`platform-*`、`govdata-*` 的平台控制库 DDL、DML、Mapper SQL 与运维 SQL
- 所有者：GovData Platform Team
- 最后更新：2026-08-04

平台控制库只正式支持 MySQL 8。外部多数据源查询遵循
[外部数据源受控查询 SQL 规范](external-query-sql.md)，不得把本文的控制库方言约束直接套用到外部数据库。

## 1. 总体原则

- 控制库是 OLTP 数据库，只承载配置、状态、授权、任务编排和轻量统计。
- 每张表只有一个模块所有者；禁止跨模块访问 Mapper、业务表或建立跨领域 JOIN。
- 平台业务表不建立物理外键；引用完整性由应用服务、事务、对账和集成测试保证。
- SQL 必须参数化、可解释、可限制影响范围，并能通过真实 MySQL 8 验证。
- 禁止存储过程、触发器、数据库事件、业务视图和任意脚本承担领域逻辑。
- MySQL 保持严格 SQL Mode，至少启用严格数据校验和 `ONLY_FULL_GROUP_BY`。

## 2. 表类型与字段模板

新表必须先声明类型，再选择字段；禁止使用“按需要增加审计字段”这类模糊描述。

### 2.1 业务实体表

用于数据源、目录、策略、标准、规则等有独立生命周期的对象：

- 雪花主键、`tenant_id`、系统稳定业务编码、名称和权属字段。
- 明确的业务 `status`。
- 并发编辑时使用 `lock_version`。
- 需要后台删除和恢复时使用 `del_flag`。
- 完整创建、修改审计字段。

### 2.2 关系或明细表

- 使用雪花代理主键，不使用联合主键。
- 保存 `tenant_id` 和两端稳定 ID。
- 使用联合唯一索引表达重复绑定不变量。
- 临时关系可以物理删除；必须保留历史的关系使用状态、版本或追加记录。

### 2.3 配置或版本表

- 保存稳定编码/聚合 ID、版本号、状态、生效时间和配置快照。
- 使用 `(tenant_id, aggregate_id, version_no)` 等唯一约束。
- 已发布版本不可覆盖；变更产生新版本。

### 2.4 任务或运行表

- 保存任务状态、触发来源、主体、开始/结束时间、稳定错误码和 `trace_id`。
- 高频运行记录不使用 `del_flag` 和 `lock_version`。
- 执行外部系统与记录结果必须拆成“事务外调用 + 短事务落库”。

### 2.5 追加记录表

授权决策、访问审计、连接测试、事件和历史只允许追加：

- 必须保存 `tenant_id`、`occurred_time`、`actor_type`、`actor_id`、`trace_id` 和结果摘要。
- 禁止业务 UPDATE 和逻辑删除。
- 不机械添加 `create_dept`、`update_by`、`update_time`。
- 必须声明保留、归档和清理策略。

## 3. 命名

- 全部使用小写 `snake_case`，禁止拼音、驼峰和含糊缩写。
- 表名使用“已登记领域前缀 + 单数业务名”，例如 `authz_policy`、`platform_secret`、
  `gd_datasource`、`gd_quality_rule`。
- 主键使用 `{entity}_id`，禁止裸 `id`；引用字段与被引用主键同名。
- 稳定业务编码使用 `{entity}_code`；Boolean 使用 `_flag`；JSON 使用 `_json`。
- 时间点使用 `_time`，日期使用 `_date`，时长带单位，如 `latency_ms`、`timeout_seconds`。
- 数量使用 `_count`；比例字段必须明确 `_rate` 或 `_percent` 的含义。
- 快照使用 `_snapshot` 或 `_snapshot_json`。
- 唯一索引、普通索引和 CHECK 分别使用 `uk_`、`idx_`、`ck_` 前缀。
- 名称不得超过 MySQL 64 字符；禁止依赖工具自动截断。
- `status`、`type`、`level`、`value` 等宽泛名称必须增加业务限定。

## 4. 主键与稳定业务编码

- 平台自研表统一使用 Java `Long` 可表达的有符号 `bigint` 雪花主键。
- 禁止 UUIDv4、字符串 UUID 和 `AUTO_INCREMENT` 作为自研控制表主键。
- MyBatis-Plus 使用 `ASSIGN_ID`；需要在插入前使用 ID 时由统一 ID 能力生成。
- 多实例必须保证雪花节点唯一并监控时钟回拨；禁止随机节点号碰运气。
- 批量插入按雪花 ID 升序排列，降低聚簇索引局部乱序。
- 前端和 JSON 把雪花 ID 当字符串处理，禁止 JavaScript `number`。
- 会被授权、事件、配置、外部接口或其他模块引用的实体必须有稳定业务编码。
- 稳定编码由后端按“登记前缀 + 雪花 ID Base36”生成，例如 `ds_1m8x4k9z`。
- 前缀必须先登记到 [稳定业务编码前缀登记表](stable-code-prefixes.md)，禁止模块自行发明未登记前缀。
- 新增/修改 Command 不接收系统稳定编码；编码创建后不可修改、不可复用。
- 稳定编码不承载地区、部门、年份、状态等可变化语义。
- 法定编号、标准编号等用户业务编号使用独立领域字段，不复用系统稳定编码。

## 5. 租户

- 所有业务实体、关系、配置、版本、任务、运行和追加记录表默认包含：
  `tenant_id varchar(20) NOT NULL`。
- 真正全平台共享的表必须显式标记 `GLOBAL` 并进入评审白名单。
- GLOBAL 表不得混入地区配置、凭据或租户业务数据。
- 子表、关系表、日志表必须冗余保存 `tenant_id`，不得只依赖父表反查。
- 租户表业务唯一索引必须以 `tenant_id` 开头；常用查询索引原则上也以其开头。
- 按 ID 查询、更新、删除仍必须受租户插件或显式租户条件保护。
- 同一事务写父子记录必须校验租户一致；禁止跨租户 Grant 和业务 JOIN。

## 6. 审计、状态、删除和乐观锁

可修改的业务实体、关系、配置和版本表统一使用：

```sql
create_dept bigint DEFAULT NULL COMMENT '创建部门ID',
create_by bigint DEFAULT NULL COMMENT '创建人ID',
create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
update_by bigint DEFAULT NULL COMMENT '更新人ID',
update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间'
```

- 禁止数据库 `ON UPDATE CURRENT_TIMESTAMP`；更新时间由应用/MyBatis 填充。
- `create_by/update_by` 可空，以支持系统任务、Agent 和迁移脚本。
- 非用户主体另存 `actor_type/actor_id`；不得把 Agent ID 塞入用户 ID。
- `create_dept` 是创建快照，不代表当前权属；当前权属使用 `owner_dept_id`。
- `status` 表达业务生命周期；`del_flag` 只表达技术可见性；`lock_version` 只表达并发版本。
- `del_flag` 只允许字符 `0/1` 并建立 CHECK；不得代表停用、撤销、归档或失效。
- 乐观锁使用 `int NOT NULL DEFAULT 0`，更新时在同一 SQL 中递增。
- 追加记录、运行历史不使用逻辑删除和乐观锁。
- 稳定编码删除后仍不可复用，唯一索引不得加入 `del_flag` 放宽约束。

## 7. 类型、NULL 与默认值

- 默认 `NOT NULL`；只有“未知、尚未发生、不适用”才允许 `NULL`。
- `NULL`、空字符串和零值必须有不同语义，禁止必填字符串 `DEFAULT ''`。
- 未发生时间用 `NULL`，禁止零日期、`1970-01-01` 和 `0000-00-00`。
- 时间统一保存 `Asia/Shanghai` 的 `datetime(3)`；MySQL、JDBC、JVM 和容器时区必须一致。
- Boolean 使用 `tinyint(1)`、`_flag` 和 `CHECK (... IN (0,1))`。
- 金额和精确指标使用按领域确定精度的 `decimal(p,s)`，禁止 float/double。
- 不写整数显示宽度，禁止 `bigint(20)`、`int(11)`。
- `text/blob/json` 不设置默认值；文件和大二进制内容进入对象存储。

默认长度基线：

| 语义 | 类型 |
|---|---|
| 雪花主键/引用 | `bigint` |
| 租户编号 | `varchar(20)` |
| 稳定业务编码 | `varchar(64)` |
| 状态、类型、环境、等级 | `varchar(32)` |
| 短名称/较长标题 | `varchar(100)` / `varchar(200)` |
| 描述摘要/URL | `varchar(500)` |
| `secret_ref` | `varchar(200)` |
| `trace_id` | `varchar(64)` |
| 主体稳定 ID | `varchar(100)` |
| ErrorCode/原因编码 | `varchar(64)` |
| 错误摘要 | `varchar(1000)` |
| 时间 | `datetime(3)` |
| 耗时毫秒 | `bigint` |

长度必须与 Jakarta Validation、Entity、接口文档和测试同步；禁止无依据统一使用 `varchar(255)`。

## 8. 字符集与编码格式

每张表显式声明：

```sql
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci
COMMENT = '明确的单数业务含义';
```

- 人类名称、标题、描述使用表默认排序规则。
- 稳定业务编码、状态、类型、环境、Connector Code、ErrorCode 等机器编码使用
  `CHARACTER SET ascii COLLATE ascii_bin`。
- 系统实体编码和 Connector Code 使用小写；枚举状态使用大写；ErrorCode 使用大写与连字符。
- Hash/HMAC 按算法使用确定长度，例如 SHA-256 十六进制使用 `char(64)`。
- 后端在领域入口严格规范化或拒绝错误大小写，不能依赖不区分大小写排序规则兼容。

## 9. JSON 准入

只有结构确因插件/Connector/规则类型而变化、不参与核心关系和授权、且有 Schema 时才允许 JSON：

- 字段以 `_json` 结尾，顶层固定为对象。
- 后端必须按类型和版本执行结构、未知字段、尺寸与敏感字段校验。
- 普通配置默认最大 8KB；快照/执行计划默认最大 64KB；更大内容进入对象存储。
- 凭据、Token、私钥、连接密码和个人敏感信息禁止进入普通 JSON。
- 需要筛选、排序、JOIN、唯一约束或授权判断的属性必须提升为正式列。
- 高频 JSON 查询使用受控生成列；禁止让 `JSON_EXTRACT` 承担核心全表查询。
- JSON 默认整体替换并受乐观锁保护；View 不原样暴露内部 JSON。

## 10. 唯一约束

- 业务唯一性必须由数据库 UNIQUE 兜底，不能只依赖“先查再插”。
- 唯一字段原则上全部 `NOT NULL`；禁止忽略 MySQL UNIQUE 允许多个 NULL 的行为。
- 唯一索引从 `tenant_id` 开始，再放稳定业务字段。
- 版本表通常使用 `(tenant_id, aggregate_id, version_no)`。
- 关系表使用租户、两端稳定 ID 和必要关系类型建立唯一约束。
- ApplicationService 将并发唯一冲突转换为稳定 ErrorCode；不使用分布式锁替代单库唯一约束。

## 11. 索引

- 每个索引必须对应明确查询、排序或唯一性约束；新表评审需列出“查询 → 索引”映射。
- 联合索引通常按 `tenant_id → 等值条件 → 范围条件 → 排序字段` 排列。
- 分页稳定排序末尾追加唯一主键。
- 禁止只给低基数 `status/del_flag/*_flag` 建单列索引。
- `del_flag` 不机械放在所有索引第二位。
- 禁止创建主键已覆盖、或现有联合索引左前缀已覆盖的重复索引。
- 单表默认不超过 5 个二级索引；超过必须提供查询证据和写放大评估。
- 大字符串、TEXT 和完整 JSON 不直接索引；覆盖索引不得无限追加大字段。
- 核心查询上线前执行 `EXPLAIN ANALYZE`，记录预估/实际行数、索引、排序和临时表。
- 禁止把强制索引提示作为常规修复；例外必须说明原因和复核周期。

## 12. SELECT、JOIN 与分页

- 禁止 `SELECT *`，显式列出需要字段；多表字段必须使用别名限定。
- 只允许 JOIN 当前模块拥有的表；租户表 JOIN 必须保证两侧 `tenant_id` 一致。
- 默认单条查询不超过 4 张表；超过应评估读模型。
- 禁止隐式 JOIN、笛卡尔积和跨模块展示字段直连对方业务表。
- 禁止 N+1；使用本模块 JOIN、批量 IN 或跨模块批量契约。IN 默认最多 500 个值。
- COUNT 删除无意义 ORDER BY 和无关 JOIN；模糊查询默认禁止前导 `%`。
- 禁止在索引列上随意使用函数或隐式类型转换。
- `FOR UPDATE` 只允许短事务按主键/唯一索引锁定。
- 普通后台分页默认 20、最大 100；OFFSET 超过 10000 改为稳定游标分页。
- 游标排序末尾必须包含唯一主键；导出使用独立任务和分批游标，不放大 pageSize。
- 普通批处理默认最多 500 行；更大批次必须评估行宽、锁和耗时。

## 13. UPDATE 与 DELETE

- 租户表单条更新至少包含 `tenant_id + entity_id`；乐观锁表还必须包含 `lock_version`。
- 单条更新影响行数必须为 1，否则区分不存在、租户不匹配和并发冲突。
- 禁止无 WHERE、`WHERE 1=1`、可选条件全空，以及只按状态/时间/名称更新或删除。
- 批量更新必须有租户、明确 ID 集合和最多 500 个 ID 的批次限制。
- 只更新用例允许字段，禁止整个 Entity 无差别回写。
- 逻辑删除同步审计字段并遵守乐观锁。
- 物理删除仅限临时数据、可重建关系、达到保留期日志或明确允许的草稿子记录。
- Policy、Grant、共享协议、决策日志、审计和事件使用撤销/归档/追加历史，禁止直接物理删除。
- 大批清理由所属模块按主键或时间索引分批执行；生产修复先用相同 WHERE 执行 SELECT/COUNT。

## 14. MyBatis 与参数化

- 简单单表 CRUD 使用 BaseMapper 和类型安全 Lambda Wrapper；复杂 JOIN/聚合/批处理写 Mapper XML。
- Java 注解只放短且固定的 SQL；复杂 SQL 不塞入注解字符串。
- 普通值全部使用 `#{}`；业务 Mapper 全面禁止 `${}`。
- 动态表、列、排序字段必须由后端枚举映射固定白名单，禁止前端原文拼接。
- 禁止对外部输入使用 `apply/last/inSql/notInSql/exists` 或原始 SQL Fragment。
- 更新/删除 Wrapper 必须在条件为空时拒绝执行。
- 禁止接受表名、字段名和任意条件对象的通用 Mapper。
- XML 复杂结果使用明确 resultMap/字段别名；SQL 片段只复用本 Mapper 内稳定列清单。

## 15. DDL 约束

- 每张表和字段必须 COMMENT；正式语义仍以领域文档为准。
- 封闭状态、类型、等级、删除标志和 Boolean 使用稳定字符串/数值加命名 CHECK。
- 不使用 MySQL `ENUM/SET`，不建立物理外键。
- 单行内数值范围和时间先后关系应使用 CHECK。
- 不默认使用分区表；只有数据量、查询和清理证据充分时专项设计。
- 定义顺序：主键；租户/编码；业务字段；状态/版本/删除；审计；PK；UNIQUE；INDEX；CHECK。
- DDL 不得包含账号、密码、主机地址、真实数据或演示数据。

## 16. 事务、锁与批处理

- 保持 MySQL 8 默认 `REPEATABLE READ`；普通查询不机械声明事务。
- 写事务位于 ApplicationService，用于一个模块拥有的表与本地 Outbox/审计。
- 外部 JDBC/SFTP/Redis/HTTP/SeaTunnel/对象存储调用不得处于数据库事务内。
- 普通写事务目标低于 1 秒，超过 3 秒告警；禁止在事务中等待用户、轮询和全量处理。
- 多行更新按稳定主键顺序；跨表写入规定统一加锁顺序。
- 只对死锁/锁等待超时做最多 3 次、带抖动的整个幂等用例重试。
- 唯一冲突、校验错误和业务失败禁止重试。
- 批量任务按游标分批提交；可靠消息使用本地 Outbox，提交后再传播。

## 17. 敏感数据与日志

- 凭据只通过 `secret_ref` 引用 `platform-secret`，禁止进入普通列和 JSON。
- 密文只允许凭据模块保存，字段使用 `_ciphertext/_nonce/key_version`；Base64 不是加密。
- 随机密文不建立查询索引；敏感等值查询使用受控 HMAC/指纹列。
- 个人信息必须标注安全级别，并在 View、导出、日志和审计摘要脱敏。
- SQL 日志默认记录 Mapper ID、SQL 模板、耗时、行数和 traceId，不记录完整绑定参数。
- 生产不打印每条 SQL；控制库慢 SQL 初始阈值 500ms，外部查询为 3000ms。
- 数据库约束异常转换为领域 ErrorCode；禁止把 SQL、结构、参数和 JDBC 原文返回前端。

## 18. 高增长表与分析

- 高增长表设计时声明日增量、行宽、在线/归档期限、清理责任、批次、索引和法规要求。
- 清理由所属模块执行，单批默认最多 5000 行；Outbox 只清理成功投递且过期记录。
- 失败任务、安全拒绝和失败事件不得与成功记录机械采用相同短保留期。
- 重型报表、趋势和历史分析进入汇总读模型、Doris 或 ClickHouse。
- 控制库禁止无时间范围的大聚合、多层子查询、JSON 全表分析和大结果排序。
- 汇总表必须说明口径、所有者、刷新时间和重建方式。

## 19. SQL 格式

- 关键字大写，标识符小写；自研对象不得使用保留字，默认不加反引号。
- 每个字段/索引/约束单独一行，4 空格缩进，逗号放行末。
- JOIN、WHERE、GROUP BY、ORDER BY、LIMIT 分行；AND/OR 混用必须括号分组。
- INSERT 必须显式列名；UPDATE 的 SET 与 WHERE 一行一个字段/条件。
- SQL 文件 UTF-8、LF、文件末尾一个换行；独立 SQL 以分号结束。
- Mapper XML 不拼接多语句；禁止以大段注释保存失效 SQL。

## 20. 自动化门禁

- 静态检查禁止新 SQL 引入 `SELECT *`、物理外键、ENUM/SET、AUTO_INCREMENT、整数显示宽度、
  不安全 `${}` 和无 WHERE 更新/删除。
- Baseline 必须在空 MySQL 8 执行；升级脚本按 manifest 顺序验证。
- 自定义 SQL、JSON、租户、乐观锁、逻辑删除和复杂分页使用真实 MySQL 8 集成测试，禁止 H2 代替。
- Entity/DDL 检查主键策略、字段、时间精度、逻辑删除和乐观锁一致性。
- 核心查询保存 EXPLAIN ANALYZE 证据。
- 例外必须通过 ADR/评审明确记录，不得用普通注释关闭规则。

提交数据库修改前必须执行 [.agents/checklists/new-table.md](../../.agents/checklists/new-table.md) 和
[.agents/checklists/sql-change.md](../../.agents/checklists/sql-change.md)。

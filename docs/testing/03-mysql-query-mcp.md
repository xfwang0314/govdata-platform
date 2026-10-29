# MySQL 查询部署与验证

- 状态：第一版实现验证
- 最后更新：2026-09-07

## 部署前置条件

1. 已有控制库按升级清单手工执行 `script/sql/upgrade/20260906_001_create_query_access_log.sql`；空库按 Baseline 清单初始化。仅新增查询访问审计表，不修改外部业务表。测试不会替现有环境执行升级。
2. 外部 MySQL 使用独立 SELECT 账号，不授予写权限、GRANT OPTION 或角色。数据源须 READ_ONLY、连接测试成功并启用。
3. SecretRef 指向用户名密码凭据，允许 `DATASOURCE_QUERY` 用途；连接测试另需其对应用途。凭据不得写入代码、日志或命令行。
4. PROD 及未知环境使用 VERIFY_IDENTITY：在 JVM 信任库配置可信 CA，并使用证书匹配的主机名。DEV/TEST 使用 REQUIRED 加密连接，不代表已校验证书身份。
5. 完成元数据采集，表须 ACTIVE 或 OUT_OF_SCOPE、有主键且字段在当前 Schema 中。MySQL database 映射 Catalog，不生成重复 Schema。
6. 为用户或 Agent 所属用户授予表资源 QUERY 权限及 DATA_QUERY 用途，配置所需行过滤、允许列和脱敏义务。注册工具不等于自动授权。

## 自动注册与安全边界

存在 Schema Provider 和执行 Adapter 时自动注册 `datasource.query_dataset`、`datasource.sample_dataset`。
调用经过 Agent Token、模块上限、配额、资源授权、只读执行与审计；不接受任意 SQL。
字段 ID 使用字符串；默认用途 DATA_QUERY。普通查询最多 50 行，样例最多 5 行。
执行器硬上限为 1000 行、100 列、offset 10000、30 秒（默认 10 秒）、单单元格 1 MiB、结果保守预算 8 MiB。
超时覆盖连接后的授权检查及取数；连接阶段另受 connectTimeout 限制。超限不返回部分结果。
无主键、启用角色、写账号、未支持字段类型/操作符一律拒绝。

查询审计追加 STARTED 和终态 SUCCEEDED / DENIED / FAILED，不存 SQL、参数、凭据或返回数据。
审计不可写时拒绝执行或拒绝交付结果；异常中断可能留下 STARTED，不能据此判定成功。
保留期与容量预算见 `docs/database/reviews/03-query-access-review.md`；尚未实现审计管理页面及自动归档。

## 可重复测试

使用 JDK 21，启动本机 MySQL 8（3306）和项目 Redis。通过进程环境提供：

- `GOVDATA_MYSQL_QUERY_TEST=true`
- `GOVDATA_TEST_JDBC_URL`：本机测试管理员 JDBC 地址
- `GOVDATA_TEST_JDBC_USER`、`GOVDATA_TEST_JDBC_PASSWORD`：测试管理员凭据

管理员须能创建/删除临时库与临时账号并授权。测试仅建立随机前缀的隔离库和账号，结束后删除；禁止针对生产环境运行。
生产 TLS 拒绝合同用例依赖本机 MySQL 的证书不在 JVM 信任库中。

```powershell
.\mvnw.cmd -pl ruoyi-admin -am '-Dtest=MySqlQueryAuthorizationE2ETest,MySqlQueryContractTest,MySqlQueryExecutionAdapterTest,DefaultAuthorizedQueryGatewayTest,QueryAuditContractTest,QueryMcpToolContributorTest,SelfDevelopedArchitectureTest,SqlStandardsTest,MetadataPersistenceContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipTests=false' test
```

覆盖真实数据库参数绑定、中文/保留字标识符、写账号拒绝、超时、大单元格、并发限流、生产 TLS 拒绝；
以及真实 SecretRef、默认拒绝、Grant 许可、行列过滤和脱敏、显式 DENY、过期、跨租户、停用表、Agent Token 调用及审计。
另比较升级和 Baseline 的实际表结构并验证审计索引执行计划。HTTP 协议契约由 platform-mcp 测试单独覆盖。

权限中心侧栏是“模块/资源类型”筛选树，Catalog、Schema、表类型可平级；实际元数据实例目录按
`数据源 → Catalog（可选）→ Schema（可选）→ 表 → 字段` 展示，不应把类型筛选树当作实例父子授权关系。

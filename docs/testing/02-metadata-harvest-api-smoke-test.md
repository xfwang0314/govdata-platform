# 元数据采集与自动修订 API 冒烟测试

- 状态：当前实现
- 最后更新：2026-08-23

## 前置条件

1. 已按 Baseline 或 Upgrade 创建元数据九张表、采集六张表和菜单权限。
2. 至少存在一个状态为 `ACTIVE`、支持元数据发现且连接测试成功的数据源。
3. 当前用户具有采集计划 CRUD/执行、运行查看、元数据目录、表修订和变更问题权限。
4. 凭据只通过 `secretRef` 读取，测试日志和响应中不得出现密码、Token 或完整连接串。

## 创建单数据源计划

调用 `POST /govdata/metadata-harvest/plans`：

```json
{
  "harvestPlanName": "核心库每日采集",
  "datasourceId": 10001,
  "catalogName": null,
  "schemaName": null,
  "maxTableCount": 1000,
  "scheduleType": "MANUAL",
  "ownerDeptId": 103,
  "planStatus": "ACTIVE",
  "rawArchiveFlag": false,
  "rules": []
}
```

验证：

- 返回计划稳定编码和 `currentPlanRevisionNo=1`。
- 同一租户对相同数据源再次创建计划返回稳定唯一性错误。
- 修改计划后修订号递增，历史运行仍引用原 `planRevisionId`。

## 首次采集

1. 调用 `POST /govdata/metadata-harvest/plans/{planId}/execute`。
2. 查询 `GET /govdata/metadata-harvest/runs/{runId}`，确认状态为 `SUCCESS`、`completeRunFlag=true`，失败明细为空。
3. 查询 `GET /govdata/metadata/assets/tree?datasourceId={datasourceId}`，确认只出现 Connector 真实返回的层级：
   - MySQL：Catalog→Table→Column；
   - Oracle：Schema→Table→Column；
   - PostgreSQL/SQL Server：Catalog→Schema→Table→Column。
4. 查询表资产详情，确认 `currentRevisionNo=1`，Column 没有独立 `authzResourceId`。
5. 查询 `GET /govdata/metadata/tables/{tableAssetId}/revisions?pageNum=1&pageSize=20`，确认存在 v1。

## 变化、删除与恢复

1. 修改字段注释后再次采集：表修订递增，变更等级为 `DOCUMENTATION_ONLY`。
2. 新增可空字段后采集：等级为 `NON_BREAKING`。
3. 删除字段或收紧可空后采集：等级为 `BREAKING`，当前结构立即生效，同时
   `GET /govdata/metadata/tables/{tableAssetId}/change-issues` 返回 `OPEN` 问题。
4. 调用 `PUT /govdata/metadata/change-issues/{issueId}/status` 更新为 `ACKNOWLEDGED`、`REMEDIATING`、
   `RESOLVED` 或 `DISMISSED`，确认关闭状态会清空活动去重键。
5. 恢复原字段后采集：复用原 `columnAssetId`，字段修订号递增，不创建新的稳定资产。

## 范围与失败边界

- 分别为数据库（Catalog）、Schema、表配置 `REGEX` 纳入和排除规则：合法表达式按完整名称匹配，大小写不敏感；非法表达式或 RE2/J 不支持的语法在保存计划时拒绝，不创建修订。

- 定时采集通过计划所属租户执行；线程池中的数据源探测和 SecretRef 访问审计必须保留同一租户上下文。
- 服务异常退出留下 `RUNNING` 互斥记录后，在默认单实例 Quartz 内存模式下重启，确认旧运行被标为 `FAILED` 并释放互斥键，新周期能够创建运行。

- 配置 Catalog、Schema 或任意过滤规则后执行，范围外既有资产不得被误标为 `MISSING`，下游查询保持可用。
- 只有无范围限制、无过滤规则且完整成功的全数据源运行才允许执行缺失检测。
- 人为制造一张表字段读取失败：成功表保持生效，运行状态为 `FAILED`，失败明细最终隔离到该表；不得回滚成功表。
- 同一数据源并发执行第二次采集应返回运行冲突；失败后允许整次重试。
- 制造超过阈值的大规模表消失，确认 Run 为 `ANOMALOUS`、`missing_suppressed_flag=1`，旧资产未被标记为 `MISSING`。
- 调用 `POST /govdata/metadata-harvest/runs/{runId}/retry`，确认产生新的 `RETRY` 运行且原运行不可变。
- 开启原始归档时，MySQL 只保存对象存储引用、哈希和大小；关闭时不得写归档记录。

## 治理和影响投影

- 使用具有 `GOVERN` 权限的主体调用 `PUT /govdata/metadata/assets/{assetId}/governance`，确认仅人工治理字段变化且乐观锁递增。
- 下游模块通过 `MetadataImpactRegistry.replace` 注册表或字段依赖后，调用
  `GET /govdata/metadata/assets/{assetId}/impacts` 能看到来源模块、消费者和依赖类型。
- 使用空依赖集合替换后投影消失；下游业务实体不被元数据模块修改。

## 安全检查

- 所有分页响应为 `R<PageResult<View>>`，详情为 `R<View>`。
- 错误响应包含稳定 `errorCode` 和 `traceId`，不包含 JDBC 原文、SQL、参数或凭据。
- DataSource Resource 只创建一次；Catalog、Schema、Table 的 Resource 父链正确，Column 不创建 Resource。

## 采集运行入口（2026-10-28）

运行记录统一从采集计划行内打开抽屉，不再提供独立采集运行页面或全部运行记录查询。
分页接口为 `GET /govdata/metadata-harvest/plans/{planId}/runs`，必需指定计划并执行计划资源查看授权；
保留状态筛选、运行详情、失败明细及整次重跑。运行数据与采集执行逻辑继续保留。
已有环境执行 `20261028_001_scope_harvest_runs_to_plan.sql`，将原菜单转换为计划下权限节点，保留角色绑定。
验收：切换两个计划应分别只展示本计划记录；状态筛选与翻页不丢失计划范围；无计划查看权限时拒绝访问。
旧 `GET /govdata/metadata-harvest/runs` 不再提供列表接口。

## 资产内版本入口（2026-10-28）

删除独立“修订与变更”菜单和页面，统一从元数据目录表资产行的“查看版本”打开抽屉，
保留自动修订、两版本比对、关联变更问题及处置。修订产生规则和历史数据不变。
变更问题分页改为 `GET /govdata/metadata/tables/{tableAssetId}/change-issues`，
移除旧 `/govdata/metadata/change-issues` GET 接口及其查询对象，后端继续验证表可见性。
原菜单20300改为目录20200下的版本查看权限，20301至20303迁到目录下，角色绑定保留；
已有环境执行 `20261028_002_merge_metadata_history_into_catalog.sql`，前后端配套发布。
验收：从两张表分别打开版本应显示对应表记录；仅有修订查看权限时不请求变更问题，
无表可见性时拒绝读取，版本详情和问题处置仍分别校验原有权限。

## 元数据工作区前端验收（2026-10-28）

- 展开连接和表，列出当前授权表下的列；默认不展开全部字段。
- 搜索一个字段，检查数据库、表和字段路径保留；点击列应使用所属表加载详情，突出并滚动到该字段。
- 详情字段表支持物理名、展示名、类型及说明检索；清除检索后仍可回到定位字段。
- 导航支持拖动、收起和恢复；窄屏切为上下布局，滚动应限制在导航、列表或抽屉内部。
- 切换两连接并让旧请求晚返回，当前选择与错误状态应保持最新；刷新后不存在的资产应清除选择。
- 前端职责拆分、授权继承及目录接口不变，不需要额外数据库升级。

本轮自动化覆盖导航状态、搜索、字段格式和页面契约，并使用隔离样例对生产导航、布局、概览与字段组件
执行浏览器交互及1024/1440/1920/390宽度截图检查；尚未登录业务环境进行真实数据联调。

## 三栏资产发现（2026-10-28）

目录统一为分类导航、分页结果和快速预览三栏，卡片/列表共用查询与分页，右侧保留完整详情、版本和治理入口。
窄屏预览使用抽屉，自动选中首项不会在窄屏主动弹窗。目录分类计数仅统计已授权、已加载的树。
列查询修正命名空间父级转换：先找到命名空间内表ID，再与获授权表范围取交集后分页；空范围直接返回空结果。
不新增表或升级SQL。验收包括表/列分类、卡片/列表切换、翻页、搜索、空结果、晚到请求、列类型和父表版本入口。

三栏浏览器样例验证使用真实导航、结果、预览与布局组件，API以隔离样例替代：
选中卡片、切换表/列、列表模式、搜索/空结果、翻页、列类型与父表版本事件已验证。
1440px并列预览，1024/390px首项不会自动弹出抽屉，点击结果可打开；主题与页面横向溢出已检查。
真实业务环境的接口与角色配置仍需部署联调，本轮无数据库结构升级。

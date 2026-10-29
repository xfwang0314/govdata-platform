# 当前实施状态

- 状态：实施基线 v1
- 适用范围：代码、SQL 与运行验证的真实边界
- 所有者：GovData Platform Team
- 最后更新：2026-09-07

本文是“当前仓库已经具备什么”的唯一入口；`docs/architecture/` 中标记为“目标”或“前瞻”的内容不代表已上线。

| 模块 | 当前状态 | 已具备 | 尚未具备 / 不得假定 |
|---|---|---|---|
| RuoYi / Workflow | 上游兼容，工作流默认关闭 | 登录、组织、RBAC、Quartz、公共基础设施；WarmFlow 依赖保留 | 工作流不属于当前阶段；不把上游示例表或旧 `update/` 当作政务领域实现 |
| platform-auth | P0 已实现 | Resource、Policy Group/Version、ABAC、Grant、批量资源范围、决策审计 | 正式 Delegation、BreakGlass、属性注册表 |
| platform-secret | P0 已实现 | SecretRef、加密版本、用途目录、解析审计 | Vault/KMS/密码机 Adapter |
| platform-mcp | P0 已实现 | Streamable HTTP、Agent 凭据、轮换/启停/吊销、OWNER_INHERIT、工具模块上限和配额 | Agent Task、显式 Delegation 聚合、写入型跨部门 Tool |
| govdata-datasource | P0 已实现 | Connector Catalog、SecretRef、连接测试、元数据探查、Resource 投影与 ABAC/Grant | SQL Server Connector、生产级连接池治理、采集任务 |
| govdata-query | MySQL 查询闭环已实现 | 结构化查询、MySQL 只读 SecretRef 执行、行列授权/脱敏、追加访问审计、自动注册数据行 MCP | 其他数据库方言、异步导出、查询审计管理页面；部署前须执行查询审计表升级 |
| govdata-metadata | P0 已实现 | 稳定技术资产、表级自动修订、ChangeIssue、治理属性、当前查询 Schema、可重建下游影响投影 | 可靠 Outbox、血缘、Dataset 和 OpenMetadata 导入 Adapter |
| govdata-metadata-harvest | P0 已实现 | 单数据源计划、不可变修订、规则、手工/Quartz Cron、有界批量写入、异常消失保护、整次重跑与原始归档 | 更多 Connector 原生对象 ID、运行中取消和失败单表定点重试 API |
| exchange / sharing / serving / standard / quality / ontology | 仅完成领域与架构设计 | 术语、边界、目标接口方向 | 没有 Maven 实现模块、表、Controller 或可运行流程 |

## 当前可验证流程

1. 启动 MySQL、Redis 与 `ruoyi-admin`，使用 Baseline 初始化空库或按 Upgrade 清单升级已有环境。
2. 登录管理端，创建凭据，再创建数据源；使用连接测试与元数据探查验证 Connector、Secret 和授权链路。
3. 创建 Agent 凭据，使用 `/platform/mcp` 连接；元数据探查及数据行查询 Tool 按实际能力自动注册，调用仍须通过模块限制、配额与资源授权。
4. 创建单数据源采集计划并手工执行，查看运行、当前目录和表修订；破坏性变化立即更新当前技术事实并产生待处置 ChangeIssue。
5. 数据行查询已具备 MySQL 执行与访问审计；部署前执行 `20260906_001_create_query_access_log.sql`，配置实际只读账号及 `DATASOURCE_QUERY` 凭据用途，再授予资源 QUERY 权限。

可重复的授权与数据源 API 验证步骤见
[测试手册](testing/01-platform-auth-datasource-api-smoke-test.md)。
元数据端到端验证见 [元数据采集与修订测试手册](testing/02-metadata-harvest-api-smoke-test.md)。
MySQL 查询部署与回归见 [查询验证手册](testing/03-mysql-query-mcp.md)。

## 实施规则

- 新能力先更新本表，再更新所属 `CONTEXT.md`、架构文档、Baseline/Upgrade 与测试。
- 设计文档可以描述目标，但必须显式标注“未实现”；不得把目标表、目标模块或目标流程写成当前能力。
- 每次新增 Upgrade，必须同步更新 `script/sql/upgrade/manifest.md`；已共享或已执行的脚本不可修改。
- WarmFlow 当前仅作为上游兼容依赖保留，默认配置关闭；待数据共享等领域确需人工审批时，再由所属模块定义流程、
  启用环境配置并补充验收手册。

## 采集运行入口（2026-10-28）

运行记录统一从采集计划行内打开抽屉，不再提供独立采集运行页面或全部运行记录查询。
分页接口为 `GET /govdata/metadata-harvest/plans/{planId}/runs`，必需指定计划并执行计划资源查看授权；
保留状态筛选、运行详情、失败明细及整次重跑。运行数据与采集执行逻辑继续保留。
已有环境执行 `20261028_001_scope_harvest_runs_to_plan.sql`，将原菜单转换为计划下权限节点，保留角色绑定。
验收：切换两个计划应分别只展示本计划记录；状态筛选与翻页不丢失计划范围；无计划查看权限时拒绝访问。
旧 `GET /govdata/metadata-harvest/runs` 不再提供列表接口。

## 元数据 Explore 导航（2026-10-28）

目录入口改为数据库分类、数据源连接、真实命名空间和表资产的树形浏览。
复用现有授权树和资产分页接口，连接按展开加载，点击连接/命名空间筛选列表，点击表查看详情。
树搜索覆盖连接名称及已加载资产；不新增 OpenMetadata 部署或数据库结构。

## 资产内版本入口（2026-10-28）

删除独立“修订与变更”菜单和页面，统一从元数据目录表资产行的“查看版本”打开抽屉，
保留自动修订、两版本比对、关联变更问题及处置。修订产生规则和历史数据不变。
变更问题分页改为 `GET /govdata/metadata/tables/{tableAssetId}/change-issues`，
移除旧 `/govdata/metadata/change-issues` GET 接口及其查询对象，后端继续验证表可见性。
原菜单20300改为目录20200下的版本查看权限，20301至20303迁到目录下，角色绑定保留；
已有环境执行 `20261028_002_merge_metadata_history_into_catalog.sql`，前后端配套发布。
验收：从两张表分别打开版本应显示对应表记录；仅有修订查看权限时不请求变更问题，
无表可见性时拒绝读取，版本详情和问题处置仍分别校验原有权限。

## 元数据工作区优化（2026-10-28）

目录导航补充列节点并支持定位表详情字段；复用已有授权目录接口，无数据库变更。
资产列表、导航状态、可拖动/收起布局、位置导航、资产概览与字段检索分离，保持页面状态本地化。
默认展示名称、物理名、类型、说明、技术状态、修订和操作，其他治理列从列设置打开。

## 三栏资产发现（2026-10-28）

目录统一为分类导航、分页结果和快速预览三栏，卡片/列表共用查询与分页，右侧保留完整详情、版本和治理入口。
窄屏预览使用抽屉，自动选中首项不会在窄屏主动弹窗。目录分类计数仅统计已授权、已加载的树。
列查询修正命名空间父级转换：先找到命名空间内表ID，再与获授权表范围取交集后分页；空范围直接返回空结果。
不新增表或升级SQL。验收包括表/列分类、卡片/列表切换、翻页、搜索、空结果、晚到请求、列类型和父表版本入口。

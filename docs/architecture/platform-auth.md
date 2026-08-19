# Platform Auth 当前实现说明

- 状态：当前实现说明
- 适用范围：`platform-api` 与 `platform-modules/platform-auth` 代码
- 所有者：GovData Platform Team
- 最后更新：2026-08-13

> 本文记录当前分支已实现的第一阶段代码。模块边界以
> [模块与依赖架构](01-module-dependencies.md) 和
> [RBAC、ABAC、Grant 与 Delegation](03-rbac-abac-grant-delegation.md) 为准。

## 定位

`platform-auth` 是与政务数据业务解耦的通用授权模块，负责：

- Resource：统一可授权资源和资源关系。
- ABAC：根据 Subject、Resource、Action、Environment 动态决策。
- Grant：当前地区租户内部的资源授权凭据。
- Obligation：行范围、字段白名单、脱敏、最大行数和导出约束。
- Audit：记录授权依据、结果、耗时和 traceId。

现有能力继续分工：

- 多租户插件是地区之间的硬隔离边界。
- Sa-Token RBAC 负责登录、角色、菜单和接口功能权限。
- `platform-auth` 负责对具体资源执行动态授权。
- 运行时数据范围统一使用 ABAC；旧 `data_scope` 字段、角色—部门范围表和计算链已移除。

## 模块边界

```text
platform-api
└─ com.xfwang.platform.api.auth       授权契约、DTO、注解（platform-api）

platform-modules/platform-auth
└─ com.xfwang.platform.auth
   ├─ resource                        资源注册
   ├─ policy                          受限条件求值
   ├─ grant                           租户内授权
   ├─ decision                        默认决策实现
   ├─ enforcement                     注解与请求上下文
   └─ audit                           决策审计
```

`platform-auth` 禁止依赖 `govdata-metadata`、`govdata-exchange` 等领域实现。
领域模块通过 API 契约注册自己的资源并调用授权服务。

## 安全不变量

1. Resource、Policy、Grant 和 Audit 表均包含 `tenant_id`，不加入租户排除表。
2. Resource 与 Subject 的租户不一致时立即拒绝。
3. Grant 只能在当前租户内部生效。
4. 明确 `DENY` 优先于 Policy 或 Grant 的 `PERMIT`。
5. 没有匹配到允许策略或有效 Grant 时默认拒绝。
6. 策略不接受任意 Java、SpEL、SQL 或脚本。
7. Mapper 和外部查询网关只能消费结构化 Obligation，不能执行策略保存的 SQL。
8. `platform-auth` 不替代 Sa-Token 的功能权限校验。

## Resource

Resource 类型使用可扩展字符串，不在授权核心中写死枚举。建议领域编码：

```text
govdata:data-source
govdata:database
govdata:schema
govdata:table
govdata:column
govdata:dataset
govdata:api
govdata:file
```

领域模块调用 `ResourceRegistry` 注册资源：

```java
ResourceDescriptor resource = ResourceDescriptor.builder()
    .resourceCode("govdata:table:mysql.population.person")
    .resourceName("人口信息表")
    .resourceType("govdata:table")
    .businessId("387")
    .sourceModule("govdata-metadata")
    .ownerDeptId(103L)
    .classification("PERSONAL_INFORMATION")
    .securityLevel(3)
    .build();

resourceRegistry.register(resource);
```

数据源、库、Schema、表、字段使用 `parent_id` 表达层级。API 与多个表、数据集
之间使用 `authz_resource_relation` 表达非父子关系。

## Policy

策略条件使用受限 JSON DSL：

```json
{
  "logic": "ALL",
  "conditions": [
    {
      "left": "subject.securityClearance",
      "operator": "GTE",
      "rightAttribute": "resource.securityLevel"
    },
    {
      "left": "environment.networkZone",
      "operator": "IN",
      "value": ["GOV_EXTRANET", "GOV_INTERNAL"]
    }
  ],
  "groups": []
}
```

第一阶段操作符：

```text
EQ NE IN NOT_IN CONTAINS GT GTE LT LTE EXISTS
```

策略需要同时绑定 Subject 和 Resource。Subject 支持：

```text
ANY HUMAN ROLE DEPARTMENT
```

第一阶段只启用 Human 相关主体。虽然完整数据模型预留了 SERVICE 和 AGENT，管理服务在可信
Service Principal 认证、Agent Identity、Task 和 Delegation 决策链启用前会拒绝为它们创建 Grant，
避免产生无法安全执行的授权记录。

Resource 可以绑定单个资源、包含其后代，或者绑定一种资源类型。

管理端的“来源模块”只用于筛选资源类型，不进入 Policy 绑定条件，也不切断跨模块资源树。
例如元数据模块注册 Schema 时，Schema 的 `parent_id` 指向数据源模块投影的 DataSource Resource；
资源查询批量补齐祖先名称并展示“数据源 / Schema / 表”路径。Policy 最终仍只保存统一的
`resource_id` 或 `resource_type`。

### Policy 生命周期

策略聚合通过 `current_version_id` 指向当前发布版本，通过兼容字段 `draft_version_id` 指向唯一待发布版本。
生命周期使用平台内部的轻量领域状态机，不引入外部工作流或状态机框架：

```text
首次创建：聚合 DRAFT，版本 v1=DRAFT（页面展示为待发布）
首次发布：聚合 DRAFT -> ACTIVE，版本 v1 DRAFT -> PUBLISHED
停用：聚合 ACTIVE -> DISABLED，当前版本仍为 PUBLISHED，但立即退出授权决策
新建版本：仅在 DISABLED 下复制当前完整文档为 vN+1=DRAFT，聚合仍为 DISABLED
发布新版本：旧版本 PUBLISHED -> SUPERSEDED，待发布版本 -> PUBLISHED，聚合 -> ACTIVE
放弃修改：待发布版本 DRAFT -> DISCARDED，原当前版本和聚合 DISABLED 状态不变
```

- PUBLISHED、SUPERSEDED 和 DISCARDED 版本均不可原地修改；修改只能发生在待发布版本上。
- 运行中的版本只能查看和停用；新建版本前必须先停用。新建版本不会立刻改变授权结果，只有发布事务同时
  切换 `current_version_id` 后才重新启用。
- `policy_document_json` 保存主体、资源、动作、条件和约束的完整策略事实；发布时由它重新编译
  Subject、Resource、Action 关联索引。关联索引可重建，不承担历史事实职责。
- 决策日志使用版本主键还原历史授权事实；策略生命周期变更写入独立的
  `authz_policy_operation_log`，两种日志不得混用。
- 待发布版本使用 `lock_version` 防止并发覆盖；同一策略同时只能存在一个待发布版本。
- `include_children` 只表示具体资源绑定是否覆盖后代资源；资源自身是否继承父策略由
  `authz_resource.inherit_policy` 决定，二者不能混用。

策略约束示例：

```json
{
  "rowFilters": [
    {
      "attribute": "regionCode",
      "operator": "EQ",
      "values": ["subject.regionCode"]
    }
  ],
  "allowedColumns": ["name", "idCard", "phone"],
  "maskingRules": {
    "idCard": "ID_CARD",
    "phone": "MOBILE"
  },
  "maxRows": 1000,
  "exportAllowed": false
}
```

`rowFilters.attribute` 是资源定义的逻辑属性，不是任意数据库列名。后续由资源适配器
将逻辑属性映射到受信任的实际列。

条件中的任一引用属性不存在或值不可比较时，整个条件按不匹配处理，包括 `NE` 和 `NOT_IN`。
Obligation 的 `allowedColumns=null` 表示该项未施加字段约束，空集合表示禁止返回任何字段；多个
白名单取交集后即使为空也必须保持拒绝语义。脱敏规则冲突时选择更严格结果，不允许 `NONE`
覆盖已经生效的脱敏要求。

## Sa-Token 接入

Controller 仍使用功能权限：

```java
@SaCheckPermission("metadata:table:query")
```

需要访问具体资源的 Service 方法使用：

```java
@ResourceAuthorize(
    resourceId = "#resourceId",
    resourceType = "govdata:table",
    action = "QUERY",
    purpose = "#request.purpose"
)
```

执行顺序：

```text
Sa-Token 功能权限
→ 解析 Resource ID
→ 从 LoginUser 构建 Subject
→ 加载 Resource、Policy、Grant
→ DENY 优先并默认拒绝
→ 将 Decision 和 Obligation 放入 AuthorizationContext
→ 执行业务方法
→ 清理上下文
→ 写入 authz_decision_log
```

定时任务、消息消费等非 HTTP 场景必须显式提供 Subject，不能因为没有 Sa-Token
请求上下文而跳过授权。

`networkZone` 不能直接信任客户端请求头。可信网关或网络识别过滤器校验后，应写入
请求属性 `authz.networkZone`，资源授权切面只读取该可信属性。

## 一次授权请求的真实代码链路

授权分为两个不同层次，不能互相替代：

```text
Controller 的 @SaCheckPermission
└─ 判断当前人能否使用这个功能或接口（RBAC）

Application Service 的 @ResourceAuthorize
└─ 判断当前人能否对某个具体 Resource 执行某个 Action（ABAC + Grant）
```

领域服务的典型入口：

```java
@ResourceAuthorize(
    resourceId = "#resourceId",
    resourceType = "govdata:table",
    action = "QUERY",
    purpose = "#request.purpose"
)
public PageResult<TableRowVO> query(Long resourceId, QueryRequest request) {
    // 只有授权决策为 PERMIT 才会进入方法体。
}
```

运行时按以下顺序执行：

1. 请求先经过 Sa-Token。Controller 上的 `@SaCheckPermission` 校验菜单和接口功能权限。
2. Spring AOP 代理调用带 `@ResourceAuthorize` 的公开 Service 方法，进入
   `ResourceAuthorizeAspect.authorize`；同类内部自调用不会触发该切面。
3. 切面使用受限 SpEL 从方法参数读取 `resourceId` 和 `purpose`，读取当前时间、IP、可信
   `networkZone` 与 MDC `traceId`，构造 `AccessRequest`。
4. `DefaultAuthorizationService` 调用 `SaTokenSubjectProvider`，把 `LoginUser` 转换为
   `SubjectContext`，包含 tenant、user、role、department 和已注册主体属性。
5. 决策服务从 `authz_resource` 加载 Resource，先执行资源存在、租户一致、状态和资源类型校验；
   任一失败立即返回 DENY。
6. 决策服务加载资源自身及最多 32 层父资源，寻找聚合状态为 `ACTIVE` 且当前版本为 `PUBLISHED`
   的不可变 Policy，
   依次匹配 Subject、Resource、Action、Environment 条件。
7. 只要命中一个显式 `DENY` Policy，立即拒绝；它优先于所有 PERMIT Policy 和 Grant。
8. 没有显式拒绝时，继续匹配当前主体的 ACTIVE Grant，并校验生效时间、失效时间、用途、
   条件、Resource、Action 和 `includeChildren`。
9. 没有任何 PERMIT Policy 或有效 Grant 时默认拒绝；否则按最严格原则合并 Obligation：
   行条件累加、字段白名单取交集、最大行数取最小值、任一禁导出即禁导出。
10. 每次决策都尝试写入 `authz_decision_log`，保存结果、原因、命中版本、Grant、Obligation、
    traceId 和耗时。
11. DENY 时切面抛出统一 403 `ServiceException`，业务方法不会执行。
12. PERMIT 时切面把 `AccessRequest + AccessDecision` 压入线程级
    `AuthorizationContextAccessor`，再执行实际业务方法；`finally` 中弹出并清理，支持嵌套调用且
    不发生线程复用污染。

`POST /platform/auth/decisions/evaluate` 是管理和排障入口。它本身使用 Sa-Token 功能权限，
但不会触发 `@ResourceAuthorize`；Controller 直接构造 `AccessRequest` 并调用同一个
`AuthorizationService`，因此决策规则和审计结果与切面路径一致。

当前 `AuthorizationContextAccessor` 已提供给 `govdata-query` 读取，查询网关核心能够执行字段
白名单、行过滤、脱敏、限量和用途绑定。元数据 Schema Provider、真实 Connector Adapter 和查询
访问审计仍未全部接入，因此外部数据访问闭环要在这些 Adapter 就绪并通过集成测试后才算完成。

## 无物理外键时的完整性责任

授权表只保存逻辑引用，不建立数据库物理外键：

- Resource 注册服务校验父资源存在且不能指向自身。
- Policy 创建服务校验显式绑定的 Resource 存在；Policy、Version、Subject、Resource、Action
  在同一事务写入。
- Grant 创建服务校验每个 Scope 的 Resource 存在；Grant 与 Scope 在同一事务写入。
- Policy 发布与 Grant 激活必须经过状态机服务，Controller 不直接访问 Mapper。
- `tenant_id` 由租户插件注入并参与所有唯一索引和查询条件，禁止跨租户引用。
- 后续增加授权关系对账任务，检查孤儿版本、绑定、Scope、无效父资源和跨租户引用。

## MyBatis-Plus 与外部查询

目标插件顺序：

```text
TenantLineInnerInterceptor
→ AbacRowPermissionInterceptor
→ PaginationInnerInterceptor
→ OptimisticLockerInnerInterceptor
```

ABAC 决策每个资源操作只计算一次，MyBatis 拦截器读取当前
`AuthorizationContextAccessor` 中的 Obligation 并安全编译行级条件。

MyBatis-Plus 只能保护平台自身数据库。MySQL、Oracle、Doris、ClickHouse、
SFTP 和外部 API 必须经过统一数据访问网关，由网关执行：

- 字段投影
- 行级过滤
- 最大行数
- 脱敏
- 导出限制
- 查询审计

## 当前实现范围

已经具备：

- 授权 API 契约。
- Resource 幂等注册与查询接口。
- Policy 聚合、不可变版本、Subject、Resource、Action 绑定及发布接口。
- 租户内 Grant 草稿、激活、撤销、来源追踪及查询接口。
- 受限 JSON 条件求值。
- 默认拒绝、明确拒绝优先的决策。
- Sa-Token Subject 适配。
- HUMAN Principal 与 ROLE/DEPARTMENT Subject Selector 分离，登录态包含当前部门和祖先部门链。
- 业务模块贡献式授权目录，统一注册 Resource Type、Action、Attribute 与 Purpose。
- `@ResourceAuthorize` 执行点。
- `@ResourceAuthorize` 支持按业务稳定 ID 解析授权投影。
- 请求级 AuthorizationContext。
- 授权决策审计。
- 数据源 Resource 提交后幂等投影、对象级执行点和分批对账入口。
- 用户、部门和角色关联查询的结构化 ABAC 数据范围桥接；角色本身及相关写操作只执行 RBAC。
- MySQL 8 第一版 11 表空库基线。
- 可通过 Swagger 执行的[授权闭环验证](platform-auth-smoke-test.md)。

尚未接管或仍需继续完善：

- 未标注 `@AbacDataScope` 的 RuoYi 存量模块只执行租户隔离与 RBAC；`sys_role.data_scope`、
  `sys_role_dept` 及旧运行时实现已经物理删除。
- `AuthorizedResourceService` 为领域列表提供分页前的可见资源集合，数据源列表已按授权资源 ID 在数据库
  查询阶段过滤；第一版实现保持接口稳定，后续可以在授权模块内替换为策略索引或缓存。
- MyBatis 行级 Obligation 编译。
- Column 投影与脱敏执行。
- 元数据 QueryResourceSchemaProvider、真实 Connector Adapter 和查询访问审计。
- 策略停用和版本修订流程。
- 用户密级、行政区划等扩展 Subject 属性提供器；当前已提供部门祖先链。

### 部门数据范围预设

管理端通过受控策略模板从六种固定部门范围批量创建 ABAC 待发布版本。一次配置可以选择多个
用户、角色和资源类型；模板按资源类型拆分策略，各类型分别声明动作和资源范围。转换关系如下：

```text
全部                         -> 无附加条件
自定义                       -> resource.ownerDeptId IN [部门ID]
本部门                       -> resource.ownerDeptId EQ subject.deptId
本部门及以下                 -> resource.ownerDeptPathIds CONTAINS subject.deptId
仅本人                       -> resource.ownerUserId EQ subject.userId
本部门及以下或本人           -> 上述两项 ANY
```

模板先提供无副作用预览，确认后在一个事务中生成多条标准待发布版本，并使用同一
模板只生成一次普通策略组待发布版本。`authz_policy_group` 保存稳定身份、生命周期指针和模板来源编码；
`authz_policy_group_version.policy_document_json` 是策略内容唯一事实。运行中的策略必须先停用，停用后通过
“开始修改”复制完整文档形成唯一待发布版本，编辑完成后原子发布；历史版本继续作为授权审计事实保留。
管理端编辑模板策略时，后端从待发布版本的标准条件反解部门范围预设和自定义部门，页面修改后再编译回
标准条件。回显参数是派生视图，不在策略组表增加独立列。
历史 Resource 必须通过所属领域投影对账重放，补齐部门路径和所有人。

旧 `DataPermission`、`DataScopeType`、角色数据范围修改接口及其运行时计算链已经移除；
`sys_role.data_scope` 和 `sys_role_dept` 已删除，前端角色页不得恢复旧配置入口。

### 系统管理首批迁移边界

用户列表、用户导出、角色已分配/未分配用户以及部门列表，通过 `@AbacDataScope` 声明稳定
Resource Type 和 Action。MyBatis 拦截器向 `DataAuthorizationScopeProvider` 请求结构化范围，平台授权模块只返回
“全部、拒绝、部门 ID 集合、是否包含本人”，不得返回 SQL。SQL 列名仍由 Mapper 注解控制。

超级管理员和当前租户的租户管理员在该拦截器中保留直通；普通用户缺少已发布允许策略时默认拒绝。

运行时只识别已发布、当前版本、主体和动作匹配，并且能够反向解析为六种受控部门范围的 ABAC Policy。
多个允许策略取并集，任何匹配的明确拒绝优先；没有可执行的允许策略时默认拒绝。用户、部门、岗位和角色数据不逐行注册
`authz_resource`，策略按 `system:user`、`system:department`、`system:post`、`system:role` 资源类型绑定。

授权目录通过 `selectionModes` 显式声明资源类型支持的绑定粒度：`TYPE` 表示按类型授权且不要求逐条投影，
`INSTANCE` 表示选择 `authz_resource` 中的具体投影。用户、部门仅声明 `TYPE`；数据源等独立治理资源声明
`TYPE + INSTANCE`。策略页面必须消费该声明，不得根据资源列表是否为空猜测授权模式。

绑定粒度不等于管理用途。每种 Resource Type 还必须声明 `capabilities`：

- `RESOURCE_CATALOG`：显示在授权资源台账。
- `CUSTOM_POLICY` / `POLICY_TEMPLATE`：分别允许自定义策略或范围模板引用。
- `QUERY_SCOPE`：可被 MyBatis 结构化数据范围适配器使用。
- `GRANT`：允许创建共享授权。
- `INSTANCE_DECISION` / `TYPE_CONTEXT_DECISION`：分别允许投影实例或可信非投影上下文参与运行时决策。

用户、部门类型只承担策略模板和查询范围语义，因此不进入资源目录、自定义策略和 Grant。数据源等需要
对象级治理的资源继续投影。质量任务等只需类型策略、无需对象级 Grant 的资源，可以由业务模块构造可信
`ResourceDescriptor`，通过 `@ResourceAuthorize(resourceContext = "...")` 进入决策；此路径不读取
`authz_resource`、不匹配 Grant，只匹配同类型已发布策略，且客户端不得直接提交授权属性。

条件属性目录同时约束值类型、运算符、左右操作数角色、基础/高级/模板可见性和稳定选项。管理端默认提供
“本人、本部门、本部门及以下、同区域、安全许可、可信网络”等常用预设，只有高级模式才展示原始属性比较；
后端始终按目录校验，`INTEGER`、集合、布尔和时间不得以任意字符串绕过。

全项目运行时不再读取 `sys_role.data_scope`：

- 用户、部门和角色关联列表读取 ABAC。
- 用户、部门和角色写操作及角色列表只执行 Controller RBAC 和租户隔离。
- 工作流、岗位等尚未接入 ABAC 的模块只执行租户隔离与 Controller RBAC；需要行范围时必须显式新增
  `@AbacDataScope`、授权目录和测试，不得恢复旧实现。

## 第一版验收边界

第一版采用 Human 主体的 `Resource + ABAC + AuthorizationGrant` 核心子集。数据库基线由以下
11 张表组成：

```text
authz_resource
authz_resource_relation
authz_policy_group
authz_policy_group_version
authz_policy_runtime_rule
authz_policy_runtime_subject
authz_policy_runtime_resource
authz_policy_operation_log
authz_grant
authz_grant_scope
authz_decision_log
```

以下能力是第一版上线门槛，不得仅以 Mapper 或手工 SQL 代替：

1. Resource 注册、停用、关系同步和对账。
2. Policy 待发布版本、校验、模拟、发布、停用、放弃修改和版本查询服务。
3. Grant 创建、激活、到期、撤销及业务来源追踪服务。
4. Policy、Grant 和授权审计的 RBAC 管理接口与页面配置能力。
5. `govdata-query` 消费并执行全部查询类 Obligation。
6. Policy、Grant、租户隔离、DENY 优先和查询约束的端到端集成测试。

`platform_service_principal`、`authz_attribute_schema`、Delegation 和 BreakGlass 保留为后续阶段设计，
不进入第一版建表和实现验收范围。

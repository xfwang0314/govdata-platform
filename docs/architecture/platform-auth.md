# Platform Auth 当前实现说明

- 状态：当前实现说明
- 适用范围：`platform-api` 与 `platform-modules/platform-auth` 代码
- 所有者：GovData Platform Team
- 最后更新：2026-08-02

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
- 现有 `data_scope` 暂时继续负责存量行级范围，迁移完成前不能删除。

## 模块边界

```text
platform-api
└─ com.xfwang.platform.auth.api       授权契约、DTO、注解

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
6. 决策服务加载资源自身及最多 32 层父资源，寻找当前 `PUBLISHED` Policy 的当前不可变版本，
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
- `@ResourceAuthorize` 执行点。
- 请求级 AuthorizationContext。
- 授权决策审计。
- MySQL 8 第一版 10 表空库基线。
- 可通过 Swagger 执行的[授权闭环验证](platform-auth-smoke-test.md)。

尚未接管：

- 原 `sys_role.data_scope`。
- MyBatis 行级 Obligation 编译。
- Column 投影与脱敏执行。
- 元数据 QueryResourceSchemaProvider、真实 Connector Adapter 和查询访问审计。
- 策略和 Grant 分页、模拟、停用及管理页面。
- 部门祖先链与用户密级等扩展 Subject 属性提供器；当前 Sa-Token 适配只提供当前部门。

在上述执行能力完成前，不得删除当前 `DataPermission`、`DataScopeType`、
`sys_role.data_scope` 或 `sys_role_dept`。

## 第一版验收边界

第一版采用 Human 主体的 `Resource + ABAC + AuthorizationGrant` 核心子集。数据库基线由以下
10 张表组成：

```text
authz_resource
authz_resource_relation
authz_policy
authz_policy_version
authz_policy_subject
authz_policy_resource
authz_policy_action
authz_grant
authz_grant_scope
authz_decision_log
```

以下能力是第一版上线门槛，不得仅以 Mapper 或手工 SQL 代替：

1. Resource 注册、停用、关系同步和对账。
2. Policy 草稿、校验、模拟、发布、停用和版本查询服务。
3. Grant 创建、激活、到期、撤销及业务来源追踪服务。
4. Policy、Grant 和授权审计的 RBAC 管理接口与页面配置能力。
5. `govdata-query` 消费并执行全部查询类 Obligation。
6. Policy、Grant、租户隔离、DENY 优先和查询约束的端到端集成测试。

`platform_service_principal`、`authz_attribute_schema`、Delegation 和 BreakGlass 保留为后续阶段设计，
不进入第一版建表和实现验收范围。

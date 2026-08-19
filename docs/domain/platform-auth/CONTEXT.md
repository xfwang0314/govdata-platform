# 平台授权

- 状态：领域词汇 v1
- 适用范围：平台授权上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-13

平台授权上下文为所有模块提供统一、可审计的运行时授权，不承担登录、共享审批或领域业务。

## Language

**Principal**:
可被认证或授权的主体，类型为 HUMAN、SERVICE 或 AGENT。
_Avoid_: 通用用户、账号对象

**Subject Context**:
一次授权决策使用的主体、角色、部门、租户、密级和委托属性快照。
_Avoid_: LoginUser 透传

**Subject Selector**:
Policy/Grant 用于选择 Subject 的绑定条件，第一版支持 ANY、HUMAN、ROLE、DEPARTMENT。
它不是独立登录身份；ROLE 和 DEPARTMENT 都来自已认证 HUMAN 的属性快照。
_Avoid_: 把部门或角色伪装成登录账号

**Resource**:
需要独立授权、挂载策略、审计或参与授权关系的领域对象最小投影，仅包含稳定标识和授权属性；
业务数据行不是 Resource。
_Avoid_: 统一业务实体、元数据副本

**Policy Group**:
授权管理页面和生命周期操作使用的稳定容器；自定义策略与模板策略都必须归属策略组。创建来源只决定编辑器与
生成方式，不改变版本、发布、停用和审计语义。创建来源是策略组不可变事实，待发布版本更新必须以后端策略组记录为准，
不得信任页面传值，也不得把模板策略降级为自定义策略目录校验。
_Avoid_: 把模板策略组当成独立运行时策略

**Policy**:
策略组内可独立参与授权决策的规则聚合，只保存当前版本、唯一待发布版本和生命周期状态；聚合状态固定为
DRAFT、ACTIVE、DISABLED。自定义策略组固定包含一条 Policy，模板策略组可以包含多条 Policy。
_Avoid_: 权限 SQL、脚本

**Policy Version**:
一次完整且不可变的授权规则事实，完整文档包含主体、资源、动作、条件和附加约束；版本状态固定为
DRAFT、PUBLISHED、SUPERSEDED、DISCARDED，其中 DRAFT 对用户展示为“待发布”。发布新版本只替换当前版本，
不覆盖历史版本；放弃修改会把对应待发布版本标记为 DISCARDED。
_Avoid_: 从多张关联索引表拼回策略事实

**Policy Runtime Index**:
从 Policy Version 完整文档编译得到的 Subject、Resource、Action 查询索引，可删除并重建，不是策略事实来源。
_Avoid_: 直接修改索引来变更策略

**Policy Operation**:
创建、保存、发布、停用和放弃待发布版本等生命周期事实，追加写入并携带操作主体、traceId 和版本引用。
_Avoid_: 用授权决策日志代替策略管理审计

**Attribute Comparison**:
Policy 条件右值可以是受限常量或授权目录中的另一属性，两者必须且只能选择一个；`EXISTS` 无右值。
属性对属性比较必须通过类型矩阵校验，例如安全等级可以进行 `INTEGER GTE INTEGER`，标量成员关系可以进行
`INTEGER IN INTEGER_SET`，禁止不兼容类型退化为字符串比较。
_Avoid_: 自由属性路径、隐式类型转换、任意表达式

**Pending Policy Version**:
策略聚合唯一可编辑的待发布版本。首次创建后可以反复保存；已有发布版本必须先停用，才能创建下一待发布版本。
发布事务使用它替换当前版本；放弃修改只结束该待发布版本，原版本保持停用。
_Avoid_: 原地修改已发布版本

**Authorization Grant**:
由共享、管理或委托等来源产生的、可运行和可撤销的明确访问授权。
_Avoid_: SharingAgreement、审批记录

**Delegation**:
HUMAN 或 SERVICE 将自身权限的有限子集在限定时间和任务内委托给 AGENT。
_Avoid_: 冒充、Token 转交

**Obligation**:
授权允许后仍必须执行的行过滤、字段投影、脱敏、限量或禁导出约束。
_Avoid_: Allow 标记

**BreakGlass Access**:
经审批、限时且强审计的跨租户应急访问。它与当前租户内的超级管理员、租户管理员运维直通不是同一能力。
_Avoid_: 用管理员直通替代跨租户审批

## RBAC 与部门边界

- RBAC 只决定用户是否能进入菜单和调用接口，不再决定 `govdata-*` 的数据范围。
- 用户所属部门、祖先部门链、角色集合均进入 Subject Context，由 ABAC 条件参与决策。
- `DEPARTMENT` Subject Selector 命中当前部门及其下级部门；只命中当前部门时使用
  `subject.deptId EQ <部门ID>` 条件。
- 运行时只保留 ABAC 数据范围：用户、部门和角色关联查询通过 `@AbacDataScope` 消费结构化范围；
  未标注的 RuoYi 存量查询只执行租户隔离与 RBAC，不再回退旧角色数据范围。
- 超级管理员和当前租户的租户管理员保留统一授权直通，包括 `@AbacDataScope` 行范围、投影资源列表和
  对象级决策；管理员身份只能由 platform-auth 的 `AdministratorAuthorizationPolicy` 统一识别，各执行入口
  禁止再次判断用户 ID 或角色编码。资源存在性、租户边界、资源状态和资源类型仍必须校验。普通用户没有
  已发布允许策略时默认拒绝。
- 同部门常规规则使用 Policy，跨部门、限时、可撤销的例外使用 Authorization Grant。
- 六种部门数据范围只作为一次性待发布版本生成预设：全部、自定义、本部门、本部门及以下、仅本人、
  本部门及以下或本人。生成后就是普通策略组版本，运行时不得读取预设编码。
- `resource.ownerDeptPathIds` 表示资源权属部门从根到自身的路径，
  `resource.ownerUserId` 表示业务资源所有人；二者不得使用授权投影记录的审计字段代替。
- 角色数据范围修改接口、`sys_role.data_scope`、`sys_role_dept` 和旧运行时计算链已经删除。
- 策略模板只负责一次性生成标准策略组待发布版本，不参与运行时决策，也没有独立生命周期。
- 所有策略使用后端生成的 `policy_group_code` 管理；管理端只分页展示策略组，不再存在成员 Policy 聚合层。
- `authz_policy_group` 只保存策略组稳定身份、生命周期指针和模板来源编码，用于主列表、完整详情、
  待发布版本回显；完整授权内容只存在于 `authz_policy_group_version.policy_document_json`。
- 编辑模板生成的待发布版本时，管理接口必须从 `policy_document_json` 反解受控模板参数供页面
  回显，保存时再编译为标准条件；不得为回显另增第二份授权事实列。
- 策略组版本固定遵循单线生命周期：创建 V1 待发布版本 → 编辑并保存 V1 → 发布 V1 → 停用 V1 →
  创建 V2 待发布版本 → 编辑并保存 V2 → 发布 V2。运行中的已发布版本只能查看和停用，禁止直接编辑、
  创建下一版本或重复发布；已停用版本不能重复停用。
- 每次“开始修改”只创建一个递增的待发布版本，后续保存不增加版本号；任一时刻最多存在一个待发布版本。
  `current_version_id` 与 `draft_version_id` 不得指向同一版本；首次待发布版本没有当前运行版本。
  存在待发布版本时禁止重新启用旧版本或删除策略组，必须发布新版本或放弃修改。
- 发布待发布版本表示部署并启用它，旧当前版本变为 SUPERSEDED；放弃修改只把待发布版本变为 DISCARDED，
  原版本仍保持停用。首次待发布版本尚无历史发布版本，可以编辑、发布或删除整个未发布策略。
- 每个资源目标独立声明动作和“指定资源/同类型全部资源”，禁止把只属于某资源类型的动作套用到其他类型。
- 模板扩展通过 `PolicyTemplateCompiler` 注册受控参数和编译逻辑，禁止页面提交任意表达式、SQL、SpEL 或脚本。

## 授权目录

- 每个业务模块通过 `AuthorizationCatalogContributor` 注册 Resource Type、Action、Attribute 和 Purpose。
- 每个 Purpose 必须声明适用的 Resource Type；策略、Grant、运行时决策和管理页面均按资源类型校验、筛选用途，
  禁止将某一领域的用途展示或应用到其他领域资源。
- Contributor 必须声明稳定 `moduleCode` 与展示用 `moduleLabel`；管理端按模块组织资源类型，不拼接领域名模拟模块名。
- Resource Type 必须通过 `capabilities` 明确声明可进入资源目录、自定义策略、策略模板、查询范围、Grant、
  实例决策或类型上下文决策。用户、部门、岗位和角色只开放 `POLICY_TEMPLATE + QUERY_SCOPE`，不得出现在资源台账、
  自定义策略资源选择器或共享授权选择器中。
- Policy、Grant 和管理页面都消费同一授权目录；禁止页面自由填写动作、属性路径和用途编码。
- 策略效果、风险等级、生命周期状态和条件运算符由 `platform-api` 枚举定义，并通过授权目录 options
  接口提供中文标签；页面不得硬编码英文选项。
- 策略效果与授权决策统一使用 `DecisionType(PERMIT/DENY)`；禁止再定义同值的 `PolicyEffect` 或领域内复制枚举。
- 目录只描述稳定 Schema，不保存领域实体，也不反向依赖业务模块实现。
- Attribute 必须同时声明值类型、可用运算符、左右操作数角色、展示层级和可选稳定值。前端按目录生成
  数字、多选、布尔、日期和枚举控件，后端按同一 Schema 严格校验 JSON 实际类型，禁止把所有固定值退化为字符串。
- 第一批启用 `govdata:datasource`，后续表、列、交换任务、质量规则、共享目录、API、文件、
  本体和 MCP Action 均按相同贡献机制扩展。
- 系统用户、部门、岗位和角色都是非投影组织资源。用户以 `dept_id` 为权属，岗位以自身 `dept_id`
  为权属，角色使用明确的 `owner_dept_id`；`create_dept` 只是审计快照，禁止用于运行时授权。
- 系统组织资源的 ABAC 动作覆盖读写两类：用户、岗位和角色支持 VIEW、EXPORT、CREATE、UPDATE、DELETE，部门支持
  VIEW、CREATE_CHILD、UPDATE、DELETE。RBAC 先校验功能接口，系统 Application Service 再使用目标用户所属
  部门、目标用户 ID 或目标部门执行结构化 ABAC 范围校验；新增用户校验目标部门，移动用户或部门同时校验原对象
  UPDATE 与新父级/部门 CREATE 权限，删除使用 DELETE。普通主体缺少对应已发布策略时默认拒绝。
- `ROLE` 作为 Subject Selector 表示“权限授予拥有该角色的用户”；`system:role` 作为 Resource Type
  表示“哪些角色记录可被管理”。岗位第一版只是 `system:post` Resource Type，不是 Subject Selector。
- 策略编辑器的角色主体候选由授权目录通过 `RoleService` 契约读取当前租户启用角色；该引导入口受策略查询 RBAC
  和租户隔离保护，但不得再套用 `system:role` 资源 ABAC，否则在创建首条角色策略前会形成授权自锁。
- 单资源操作由注解触发决策；列表查询必须通过 `AuthorizedResourceService` 一次批量解析当前主体的
  `AuthorizedResourceScope`，管理员返回 `UNRESTRICTED`，普通主体返回受限 Resource ID 集合或默认拒绝。
  批量解析必须一次加载主体和各运行时索引，禁止枚举资源后逐个调用对象决策形成 N+1 查询；列表解析不写
  逐资源决策日志，详情、修改、删除、测试和导出等对象操作仍完整审计。每个领域用 application 范围模块
  封装 Resource Type、Action 和 Purpose，再将结构化范围合入本领域查询，授权中心不得拼接领域 SQL。
- 单资源决策、投影资源范围、平台控制库行范围和 Grant 必须复用同一个 `SubjectSelectorMatcher`；运行时 Policy
  的 action、purpose、subject 候选预筛选及资源继承命中必须复用 `RuntimePolicyRuleMatcher`。各入口只能保留自身的
  资源层级加载、条件解释和输出转换，禁止复制主体类型或 `includeChildren` 的匹配分支。
- 来源模块只负责管理端目录筛选，不是资源父节点或授权条件；跨模块层级统一使用 Resource `parentId`，
  资源选择器展示祖先路径，避免选择 Schema、表或列时丢失其数据源上下文。
- 不需要对象级治理、Grant 和独立审计的类型资源可以不投影；运行时由业务模块从可信业务对象构造
  `ResourceDescriptor` 并通过 `resourceContext` 求值。该能力只对声明 `TYPE_CONTEXT_DECISION` 的类型开放，
  只匹配类型策略，不参与 Grant 和资源后代关系。

## 分类与等级

- `DataClassification` 是跨领域统一的安全分类：公开、内部、敏感、受限，不代表国家秘密密级。
- `SecurityLevel` 是跨领域统一的主体许可等级与资源安全保护等级，第一版固定为 1—5。
- 各领域只能引用上述 `platform-api` 契约，不得复制同义枚举或自行维护 1—5 选项。
- 若后续确有国家秘密管理需求，应独立建模非密、秘密、机密、绝密及其审批规则，不与安全分类混用。

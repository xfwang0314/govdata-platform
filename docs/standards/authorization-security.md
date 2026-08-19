# 授权与安全开发规范

- 状态：v1（强制）
- 适用范围：认证、授权、数据访问、Agent 和 MCP
- 所有者：GovData Platform Team
- 最后更新：2026-08-11

- tenantId 来自可信认证上下文；跨租户 Grant 被禁止。
- RBAC 校验功能入口，ABAC 校验具体 Resource；任一缺失不得默认放行。
- 用户、部门等平台控制库对象的新增、修改、删除同样属于具体数据操作，必须分别使用稳定的 CREATE/CREATE_CHILD、
  UPDATE、DELETE 动作执行 ABAC，不能因调用者已有菜单按钮权限而跳过。写操作校验必须位于 Application Service，
  Controller 只保留 RBAC 和协议适配，保证批量、内部调用和非页面入口采用同一规则。
- 全项目运行时数据范围统一使用 ABAC；禁止读取角色绑定部门、`data_scope` 或旧
  `DataPermission` 作为授权依据。未迁移的 RuoYi 查询只执行租户隔离与 RBAC，不得回退旧范围。
- 部门是 Subject 属性和 Policy/Grant Selector，不是 Principal。`DEPARTMENT` Selector 默认包含下级，
  精确部门约束使用 `subject.deptId`。
- 新 Resource Type、Action、Attribute、Purpose 必须由所属模块通过授权目录贡献，管理端不得接收自由编码。
- Purpose 必须显式声明适用的 Resource Type；后端必须校验用途与资源类型的组合，前端必须消费资源类型下发的
  `purposes`，不得把全局用途列表直接绑定到策略或 Grant 编辑器。
- Resource Type 必须声明授权粒度 `selectionModes`：非投影集合资源使用 `TYPE`，独立投影资源使用
  `INSTANCE`，需要两种授权方式时同时声明。前端不得根据资源列表是否为空推断授权粒度。
- 对象写入与授权 Resource 投影使用提交后事件；投影缺失、属性未知或目录不匹配时必须 fail-closed，
- 领域列表必须在数据库分页前消费 `AuthorizedResourceService` 的资源集合进行过滤；禁止先分页再逐行
  删除无权记录，否则页总数和跨页结果不可信。列表集合解析不得产生逐行决策审计，具体对象操作仍必须审计。
  并支持按业务稳定键幂等重放。
- `AuthorizedResourceService` 必须返回结构化 `AuthorizedResourceScope`：管理员使用 `UNRESTRICTED`，普通主体
  使用受限 Resource ID 集合或默认拒绝。实现必须批量加载策略、主体、资源、动作和 Grant 索引，禁止遍历资源
  调用单对象 `AuthorizationService.decide`；业务查询在 `UNRESTRICTED` 时不得生成全量 ID 的 `IN` 条件。
- DENY 优先，错误和未知属性默认拒绝。
- 条件任一引用属性缺失或值不可比较时整个条件不匹配；`NE`、`NOT_IN` 也不得因缺失属性放行。
- Policy 使用受限 JSON DSL和注册属性，禁止 SQL、SpEL、Groovy、JavaScript。
- Obligation 必须在 govdata-query、服务、导出或对应执行点真正执行。
- 字段白名单 `null` 表示未施加字段约束，空集合表示禁止全部字段；多个白名单取交集后不得把空集解释为无限制。
- 多个脱敏 Obligation 冲突时只允许保持或增强保护，`NONE` 不得覆盖已有脱敏规则。
- 需要平台控制库行范围的 Mapper 必须使用 `@AbacDataScope` 声明稳定 Resource Type、Action 和受信任列映射，
  只消费结构化 `DataAuthorizationScope`；授权模块禁止拼接或返回 SQL。
- 缺少 Provider、请求不受支持、策略不可解析或无允许策略时默认拒绝，不存在兼容回退分支。
- ABAC 范围 Provider 读取策略索引、主体/动作绑定和部门树时，必须通过 `AbacDataScopeHelper.ignore`
  进入内部查询上下文，防止数据范围拦截器递归调用自身；该上下文不得关闭租户隔离。
- 超级管理员和当前租户的租户管理员保留统一授权直通，包括行范围、投影资源列表和对象级决策；直通不得
  绕过资源存在性、租户、状态及类型校验。普通用户必须通过 ABAC；跨租户访问仍只能走 BreakGlass，并必须
  审批、限时和审计。
- 管理员身份由 platform-auth 的 `AdministratorAuthorizationPolicy` 唯一识别。对象决策和结构化数据范围
  必须调用该规则；MyBatis Handler、业务查询和 Controller 禁止重复判断管理员用户 ID 或角色编码。
- SecretValue、Token、私钥不进入日志、响应、事件或 Agent 上下文。
- Agent 不冒充用户；Delegation 不得超过用户权限、Agent 能力和任务范围。
- MCP 每次 tools/call 重新校验，不把入口令牌透传给下游。
- 高风险操作（导出、共享、策略发布、应急访问）需要明确用途和必要审批。
- 缓存键包含租户、主体、资源、动作和策略版本；撤销/发布立即失效。
- 策略版本禁止原地修改已发布事实，且每个策略组最多一个待发布版本。运行中的已发布版本只允许查看和停用；
  当前运行版本与待发布版本必须是两个不同版本，首次待发布版本不得同时登记为当前运行版本；
  创建下一版本前必须先停用。一次修改会话只创建一个版本，保存不得递增版本号。存在待发布版本时禁止重新启用
  旧版本或删除策略组；发布会替换并启用新版本，放弃修改只结束待发布版本，原版本保持停用。重复发布、重复停用、
  运行中编辑和运行中创建版本必须由后端拒绝，不能只依赖页面隐藏按钮。

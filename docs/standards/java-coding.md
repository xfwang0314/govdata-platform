# Java 开发规范

- 状态：v2（强制）
- 适用范围：所有新建和修改的 Java 代码
- 所有者：GovData Platform Team
- 最后更新：2026-08-04

包结构、依赖方向、事务和授权边界见 [Java 分层与包结构规范](java-layering.md)，测试要求见
[测试与质量门禁](testing.md)。

## 命名空间

- RuoYi 脚手架保留 `org.dromara.*`。
- 自研平台代码使用 `com.xfwang.platform.*`。
- 政务数据领域代码使用 `com.xfwang.govdata.*`。

## 接口对象

- `Command` 表达创建、修改、发布、启停等写意图；规则明显不同时拆分 Create/Update Command。
- `Query` 只表达筛选条件；分页统一使用 `PageQuery`，不得重复声明 `pageNum/pageSize`。
- `View` 表达自研 HTTP 后台接口输出；同一接口不得同时出现 View/VO 两套模型。
- `DTO` 仅用于跨模块稳定契约或外部 Adapter 数据传输。
- `Result` 表达跨模块 Java Interface 的返回结果，不使用 HTTP View。
- `BO` 只保留在 RuoYi 兼容代码；新增 `com.xfwang.*` 代码不得新增 BO。
- Command、Query、View、DTO、Result 不继承 `BaseEntity` 或 `TenantEntity`。
- HTTP Controller 可以直接使用同一实现模块 Application 层的 Command、Query、View。
- 外部协议和跨模块 Interface 使用独立契约对象，不复用实现模块对象。

## Entity

- 自研持久化 Entity 统一放在实现模块的 `domain.entity`，模块内不创建字段重复的第二套持久化对象。
- Entity 可以继承 RuoYi 的 `BaseEntity`、`TenantEntity`，并使用 MyBatis-Plus 映射注解。
- Entity 不得进入 Controller 响应、跨模块 Interface、领域事件或其他领域模块。
- 有生命周期的 Entity 维护自身状态变化和不变量；日志、历史等记录型 Entity 可以只包含字段。

## Controller

- Controller 只做协议适配、Jakarta Validation、RBAC、审计注解、调用 ApplicationService 和包装 `R<T>`。
- Controller 不声明业务事务，不直接调用 Mapper，不返回 Entity，不拼接 Wrapper，不包含领域判断。
- JSON 接口统一返回 `R<T>`；分页统一返回 `R<PageResult<View>>`。

## 校验与业务规则

- Jakarta Validation 负责必填、长度、格式和数值范围等单对象输入约束。
- ApplicationService 负责权限、事务、唯一性、加载数据和外部能力编排。
- Entity 负责自身不变量；涉及多个对象的纯规则放 DomainService。
- DomainService 不访问 Mapper、缓存、登录上下文或外部系统。

## 转换

- `Mapper` 专指 MyBatis 数据访问接口。
- `Assembler` 负责 Entity、Command、View 等模块内部对象组装。
- `Converter` 只用于值类型或协议类型转换。
- 简单且不复用的转换可以留在 ApplicationService；敏感字段或复用转换必须提取 Assembler。
- 可以使用 `MapstructUtils`/MapStruct 减少机械赋值，但 ID、租户、状态、版本和敏感字段必须显式控制。

## 类型与错误

- 时间统一使用 `LocalDateTime`；第三方适配边界使用 `Date` 后必须立即转换。
- 金额和精确小数使用 `BigDecimal`，禁止使用 float/double。
- 枚举持久化稳定 code，禁止保存 ordinal。
- 继续使用统一 `ServiceException` 和全局异常链；每个领域定义稳定 `ErrorCode`。
- 禁止 `RuntimeException("中文")` 和前端解析 `msg` 判断错误类型。
- Adapter 必须把外部异常转换为稳定错误；敏感原文只允许进入受控日志。

## 稳定编码与选项

- 会改变业务分支、数据库约束、授权判断或状态机的封闭值必须使用 Java 枚举稳定 code、服务端校验和
  MySQL CHECK 共同约束，不以可编辑 `sys_dict` 作为事实来源。
- 前端通过 options Interface 获取固定选项，通过 Catalog Interface 获取 Connector 等动态选项。
- 前端不得自由输入或硬编码 Connector 版本、状态、密级等系统编码。
- `sys_dict` 只用于允许管理员调整的展示型字典；稳定 code 不可复用。

## 可测试性与日志

- 依赖通过构造器注入，不在方法内部创建外部客户端。
- 只有跨模块稳定能力、真实多实现或生产/测试 Adapter 时才定义 Java interface，禁止透传接口。
- 日志使用参数化模板，不拼接字符串；凭据、Token、连接串和敏感查询内容不得进入日志。
- 业务失败由全局异常处理器返回统一 `errorCode` 和 `traceId`。

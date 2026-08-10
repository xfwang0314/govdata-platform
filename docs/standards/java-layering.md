# Java 分层与包结构规范

- 状态：v1（强制）
- 适用范围：所有 `com.xfwang.*` 自研实现模块与契约模块
- 所有者：GovData Platform Team
- 最后更新：2026-08-04

## 架构选择

全项目通过 Maven 模块实施战略领域设计，采用模块化单体优先。实现模块使用 RuoYi 技术基础和
实用四层结构，不强制纯 DDD 双模型、每表 Repository 或每方法 UseCase。

## 实现模块模板

```text
{module-root}
├─ interfaces
│  ├─ controller
│  ├─ listener
│  └─ scheduler
├─ application
│  └─ {capability}
│     ├─ command
│     ├─ query
│     ├─ view
│     ├─ assembler
│     └─ XxxApplicationService.java
├─ domain
│  ├─ entity
│  ├─ enums
│  ├─ event
│  ├─ exception
│  └─ service
└─ infrastructure
   ├─ persistence
   │  └─ mapper
   ├─ adapter
   ├─ connector
   ├─ client
   └─ config
```

- 只创建实际使用的包，不创建空目录。
- 简单模块可以省略 Application 下的 capability 层；出现两个以上独立用例组时按能力拆分。
- 禁止模块根包直接出现 `controller`、`mapper`、`service`、`model`。
- 禁止无边界的 `common`、`utils`、`helper`、`bean`、`pojo`、`misc` 杂物包。
- 定时入口属于 `interfaces.scheduler`；消息消费者属于 `interfaces.listener`。
- Quartz、Redis Stream、外部 SDK 和协议实现属于 infrastructure。

## 层的职责

| 层 | 职责 | 允许依赖 | 禁止依赖 |
|---|---|---|---|
| interfaces | HTTP、消息、调度等协议适配和 RBAC | application、稳定契约、Web 基础能力 | Mapper、Entity、外部 SDK |
| application | 用例编排、短事务、租户、ABAC/Grant、唯一性和外部调用协调 | domain、Mapper、Adapter Interface、API 契约 | Controller、其他模块实现 |
| domain | Entity、枚举、事件、自身不变量和纯领域规则 | JDK、模块领域类型、统一异常契约 | application、interfaces、Mapper、登录上下文、外部 SDK |
| infrastructure | 持久化、Connector、客户端、加密、框架配置和 Adapter | domain、RuoYi 基础设施、外部 SDK | interfaces、其他领域实现 |

## Domain 持久化白名单

只有 `domain.entity` 可以使用：

- MyBatis-Plus 映射注解，例如 `@TableName`、`@TableId`、`@TableField`、`@TableLogic`、`@Version`。
- RuoYi `BaseEntity`、`TenantEntity`。

所有 domain 包均禁止 `BaseMapper`、`BaseMapperPlus`、`IService`、`ServiceImpl`、`QueryWrapper`、
`LambdaQueryWrapper`、`Page`、`SqlSession`、`@Mapper`、Sa-Token、Servlet 和外部 SDK。

## ApplicationService 与 Interface

- ApplicationService 按相关用例组拆分；禁止一个模块只有不断膨胀的总管 Service。
- 不采用每个方法一个 UseCase 类的机械拆分。
- 单实现内部 ApplicationService 使用具体类，不强制 `IXxxService + XxxServiceImpl`。
- 跨模块稳定能力放 API 契约模块；真实多实现或生产/测试 Adapter 才建立内部 seam。
- Java interface 按能力命名，例如 `SecretProvider`、`ResourceRegistry`，不使用 `I` 前缀。

## Mapper 与 Repository

- 普通 CRUD 允许 ApplicationService 直接调用本模块 infrastructure Mapper。
- Controller、Entity、DomainService 和其他领域模块禁止调用该 Mapper。
- 复杂 SQL 封装为 Mapper 方法，不在 ApplicationService 散落 Wrapper 拼接。
- 只有多表聚合保存、版本/快照/事件一致性、复合或可替换存储时才建立 Repository Interface。
- 禁止为每张表创建只透传 Mapper 的 Repository。

## 事务与外部调用

- 写事务声明在 ApplicationService 公开用例方法，统一 `@Transactional(rollbackFor = Exception.class)`。
- Controller、Entity、DomainService、Mapper 不声明业务事务。
- 一个事务只修改本模块拥有的业务表；业务表与本地 Outbox 可以在同一事务写入。
- JDBC 连接测试、SFTP、HTTP、Redis 探测、SeaTunnel 等外部调用不得处于数据库长事务中。
- 外部调用采用“读取上下文 → 事务外调用 → 短事务记录结果”的结构。

## 授权位置

```text
身份认证 → interfaces RBAC → application 租户边界 → application ABAC/Grant → 领域规则 → 持久化/外部调用
```

- Controller、消息、调度、MCP 等入口负责对应主体的功能权限。
- ApplicationService 负责具体资源动作授权，列表查询同样必须应用授权约束。
- Entity 和 DomainService 不读取 LoginHelper、Sa-Token、HTTP 请求或线程登录状态。
- Mapper 不承担业务授权；默认拒绝、DENY 优先和租户隔离是强制不变量。

## API 契约模块

`platform-api`、`govdata-api` 不套四层，按稳定能力分包：Interface、command、query、result、event
和必要稳定值对象。契约模块禁止 Controller、Entity、Mapper、ServiceImpl 和外部 SDK。

- 模块间 Java 调用不包装 HTTP `R<T>`。
- 契约不暴露 MyBatis Page/Wrapper、Servlet、数据库连接和实现模块对象。
- `platform-api` 禁止依赖 `govdata-api` 或任何实现模块。
- `govdata-api` 可以依赖 `platform-api`，禁止依赖实现模块。
- 跨模块共享的封闭枚举和值对象放契约模块；业务模块拥有的开放用途不得上移为全局枚举。
- 平台实现需要发现各业务模块定义时，在契约模块声明 Contributor Interface，由业务模块的 infrastructure Adapter 实现；
  平台实现只能依赖 Contributor Interface，不得硬编码或依赖业务实现模块。

## 自动检查

ArchUnit 至少检查 Controller 不依赖 Mapper/Entity、domain 持久化白名单、API 模块纯度、禁止旧根级包、
平台代码不依赖政务实现以及领域模块之间不访问 Mapper/Entity/实现类。Maven Enforcer 继续检查 artifact
方向和直接依赖声明。

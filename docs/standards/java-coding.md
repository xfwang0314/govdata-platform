# Java 开发规范

- 状态：v1（强制）
- 适用范围：所有新建和修改的 Java 代码
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 包与分层

- 脚手架保留 `org.dromara.*`；平台代码使用 `com.xfwang.platform.*`；领域代码使用 `com.xfwang.govdata.*`。
- 实现模块按 `interfaces / application / domain / infrastructure` 组织。
- Controller/消息消费者属于 interfaces；事务和用例编排属于 application；领域规则属于 domain；
  Mapper、Entity 和外部 Adapter 属于 infrastructure。
- Domain 禁止依赖 Spring、MyBatis、Sa-Token、Servlet 和具体外部 SDK。

## 对象

- Command 表达修改意图，Query 表达查询条件，View/VO 表达输出，Entity 只用于持久化。
- DTO、BO、VO、Command、Query 不继承 `BaseEntity` 或 `TenantEntity`。
- Controller 不返回 Entity，不直接调用 Mapper。
- 跨模块只使用稳定契约对象，不传递 Wrapper、Page、HttpServletRequest 或数据库连接。

## 类型与错误

- 时间统一使用 `LocalDateTime`；只有第三方适配边界可以临时使用 `Date` 并立即转换。
- 金额和精确小数使用 BigDecimal；禁止 float/double 表达精确值。
- 业务失败抛出携带稳定 ErrorCode 的异常，不使用 `RuntimeException("中文")` 作为协议。
- 枚举持久化使用稳定 code，禁止保存 ordinal。

## 可测试性

- Module 接收依赖，不在方法内部创建外部客户端。
- 优先返回结果而非隐藏副作用；Interface 是调用方和测试共同使用的 seam。
- 只有真实存在生产和测试/替代 Adapter 时才提取 port，避免无意义透传接口。


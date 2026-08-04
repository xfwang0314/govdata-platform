# GovData Platform Agent Guide

本文件是仓库内 Agent 和开发人员的强制规则入口。详细设计以 `docs/` 为唯一事实来源，
`.agents/` 只保存执行清单和模板。

## 开发前必读

- 先阅读 [上下文地图](CONTEXT-MAP.md) 和目标领域的 `CONTEXT.md`。
- 架构设计见 `docs/architecture/`，开发规范见 `docs/standards/`，关键取舍见 `docs/adr/`。
- 当前采用模块化单体；不得自行引入微服务、跨模块数据库访问或第二套响应模型。

## 强制边界

- `org.dromara.*` 保留给 RuoYi 脚手架；自研平台代码使用 `com.xfwang.platform.*`，政务数据领域代码使用 `com.xfwang.govdata.*`。
- `ruoyi-*` 禁止依赖 `platform-*`、`govdata-*`；`platform-*` 禁止依赖 `govdata-*` 实现。
- `govdata-api` 和 `platform-api` 只放稳定契约，禁止 Entity、Mapper、Controller、ServiceImpl。
- 领域模块禁止访问其他领域的 Mapper、Entity、ServiceImpl 或业务表；同步协作走契约，状态传播走事件。
- 聚合 POM 不得注入业务依赖；每个模块显式声明直接依赖。
- `govdata-*` 禁止使用旧 `DataPermission`、`DataScopeType` 和 `sys_role.data_scope`；数据资源统一通过 ABAC 执行。

## 强制编码规则

- JSON 接口统一使用 `R<T>`；分页统一使用 `R<PageResult<VO>>`，不得新增第二套响应包装。
- Java 时间统一使用 `LocalDateTime`；DTO、BO、VO 不继承持久化基类。
- Controller 不直接调用 Mapper，不返回 Entity。
- 外部数据查询必须经过 `govdata-query`；批量交换必须经过 SeaTunnel。
- 凭据只能通过 `secretRef` 和 `SecretProvider` 使用，禁止进入日志、响应或 Git。
- 策略、查询和质量规则不得执行任意 SQL、SpEL、Groovy 或 JavaScript。
- 新表和升级 SQL 遵循 `docs/standards/mysql-sql.md` 与 `manual-database-upgrade.md`。

## 修改要求

- 修改代码时同步更新相关测试、SQL 和文档。
- 新模块、新接口、新表、新 Connector、新策略分别执行 `.agents/checklists/` 对应清单。
- 提交前执行目标模块测试、架构规则和必要集成测试；不得通过跳过测试规避失败。
- 不修改无关用户文件，不自动提交或推送，除非用户明确要求。


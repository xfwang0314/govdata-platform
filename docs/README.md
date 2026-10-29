# 政务数据中台文档中心

- 状态：文档导航与命名规范 v1
- 所有者：GovData Platform Team
- 最后更新：2026-08-21

本文档是仓库文档的统一入口。先确认“已实现边界”，再阅读架构设计和领域约束；架构目标不等同于当前可运行能力。

## 推荐阅读顺序

1. [当前实施状态](implementation-status.md)：确认模块、SQL 与运行能力的真实状态。
2. [上下文地图](../CONTEXT-MAP.md)：了解领域边界与跨模块协作关系。
3. [总体架构](architecture/00-overview.md) 与 [模块依赖](architecture/01-module-dependencies.md)。
4. 对应领域的 `domain/<bounded-context>/CONTEXT.md`：了解术语、职责和不变量。
5. [开发规范](standards/) 与相关检查清单：作为实现和评审依据。
6. [数据库设计](database/)、[SQL 脚本说明](../script/sql/README.md) 与 [测试手册](testing/)：用于交付验证。

## 文档分区

| 位置 | 用途 | 审阅重点 |
|---|---|---|
| `docs/implementation-status.md` | 当前已实现、未实现和可验证流程的唯一入口 | 不将目标能力误认为现成功能 |
| `docs/architecture/` | 跨领域架构、边界和关键流程 | 设计是否符合模块边界 |
| `docs/adr/` | 已确认且不应随意回退的架构决策 | 决策背景和后果 |
| `docs/domain/<context>/CONTEXT.md` | 领域语言、职责、状态与不变量 | 领域模型是否一致 |
| `docs/standards/` | 编码、分层、SQL、测试等强制规范 | 是否可以直接作为开发准则 |
| `docs/database/` | 控制库表设计、演进和评审记录 | 表设计与升级路径 |
| `docs/testing/` | 可重复执行的验收、联调和冒烟步骤 | 前置条件、预期结果和清理方式 |
| `script/sql/` | 可执行的 Baseline 与前向 Upgrade 脚本 | 脚本顺序和不可变历史 |

## 命名规范

- 文件名统一使用小写英文和连字符：`kebab-case.md`；保留文件名仅限 `README.md`、`CONTEXT.md`。
- 有固定阅读顺序的目录使用两位序号：`00-overview.md`、`01-module-dependencies.md`。
- ADR 使用四位序号：`0008-agent-identity-and-mcp.md`；编号一经发布不复用。
- 领域文档固定为 `docs/domain/<bounded-context>/CONTEXT.md`，目录名使用稳定限界上下文英文名。
- 测试手册使用 `NN-<scope>-<kind>.md`，例如 `01-platform-auth-datasource-api-smoke-test.md`。
- 运行手册、规范和状态文档不强制序号；名称直接表达范围，例如 `error-codes.md`、`implementation-status.md`。
- 文档开头必须包含状态、适用范围、所有者和最后更新日期；明确区分“已实现”“安全骨架”“目标设计”“历史归档”。
- 不新增以具体模块命名的“当前实现说明”或零散冒烟文档；实现状态写入 `implementation-status.md`，领域细节写入对应 `CONTEXT.md`，验证步骤归入 `testing/`。

## 当前重点入口

- 授权：[`architecture/03-rbac-abac-grant-delegation.md`](architecture/03-rbac-abac-grant-delegation.md)、[`architecture/04-resource-model.md`](architecture/04-resource-model.md)、[`domain/platform-auth/CONTEXT.md`](domain/platform-auth/CONTEXT.md)
- MCP 与 Agent：[`architecture/14-agent-delegation-mcp.md`](architecture/14-agent-delegation-mcp.md)、[`domain/agent/CONTEXT.md`](domain/agent/CONTEXT.md)
- 数据源与查询：[`domain/datasource/CONTEXT.md`](domain/datasource/CONTEXT.md)、[`architecture/06-query-gateway.md`](architecture/06-query-gateway.md)、[`domain/query/CONTEXT.md`](domain/query/CONTEXT.md)
- 授权与数据源联调：[`testing/01-platform-auth-datasource-api-smoke-test.md`](testing/01-platform-auth-datasource-api-smoke-test.md)
- 元数据采集与发布：[`architecture/07-metadata.md`](architecture/07-metadata.md)、[`domain/metadata/CONTEXT.md`](domain/metadata/CONTEXT.md)、[`testing/02-metadata-harvest-api-smoke-test.md`](testing/02-metadata-harvest-api-smoke-test.md)

# 政务数据中台上下文地图

当前模块的真实实施边界先查阅 [文档中心](docs/README.md) 和
[当前实施状态](docs/implementation-status.md)；下述领域关系同时包含已实现和目标设计。

## 上下文

- [平台授权](docs/domain/platform-auth/CONTEXT.md) — 统一 Resource、Policy、AuthorizationGrant、Delegation 与授权决策。
- [数据源](docs/domain/datasource/CONTEXT.md) — 管理连接实例、凭据引用和 Connector 能力。
- [受控查询](docs/domain/query/CONTEXT.md) — 统一消费授权约束并执行外部数据在线只读查询。
- [元数据](docs/domain/metadata/CONTEXT.md) — 管理当前技术资产、自动修订历史、结构差异和治理属性；采集执行由 Metadata Harvest 上下文承担。
- [数据交换](docs/domain/exchange/CONTEXT.md) — 管理交换任务并委托 SeaTunnel 执行数据传输。
- [数据共享](docs/domain/sharing/CONTEXT.md) — 管理租户内部共享申请、协议、订阅和交付。
- [数据服务](docs/domain/serving/CONTEXT.md) — 将受控数据查询发布为版本化数据 API。
- [数据标准](docs/domain/standard/CONTEXT.md) — 管理数据元、代码集、标准版本和落标评估。
- [数据质量](docs/domain/quality/CONTEXT.md) — 管理质量规则、检查、问题、整改和评分。
- [本体](docs/domain/ontology/CONTEXT.md) — 管理领域概念、关系、版本和语义映射。
- [Agent](docs/domain/agent/CONTEXT.md) — 管理 Agent 身份、委托、任务、工具和风险约束。

## 关系

- **数据源 → 元数据**：数据源提供连接和发现能力，元数据保存规范化采集结果。
- **元数据 → 其他治理上下文**：通过契约和版本化事件提供稳定资源标识，不共享业务表。
- **数据交换 → SeaTunnel**：交换上下文生成执行计划，SeaTunnel 负责传输、Checkpoint 和恢复。
- **数据共享 → 平台授权**：共享协议生效后投影为 `AuthorizationGrant`。
- **数据共享 → 数据交换/数据服务**：分别完成复制交付与在线 API 交付。
- **数据标准 → 数据质量**：标准可以提出规则建议，质量上下文拥有最终质量规则。
- **本体 ↔ 各领域**：通过语义映射连接概念、Action 与各领域 Resource，不接管领域事实。
- **Agent → platform-mcp → 各领域**：Agent 通过受控 MCP Tool 调用领域 Application 接口。
- **平台授权 → 所有领域**：统一执行租户边界、RBAC、ABAC、Grant、Delegation 与 Obligation。

## 全局不变量

- 租户代表独立地区，跨租户 `AuthorizationGrant` 被禁止。
- 每张业务表只有一个拥有者；跨领域只引用稳定 ID 或必要快照。
- 跨模块默认最终一致；关键激活和撤销流程必须等待处理确认。
- Resource 是授权投影，不是统一业务实体大表。

## 开发规范入口

- [Java 开发规范](docs/standards/java-coding.md)
- [Java 分层与包结构](docs/standards/java-layering.md)
- [测试与质量门禁](docs/standards/testing.md)
- [Java 功能开发清单](.agents/checklists/java-feature.md)
- [MySQL 8 与控制库 SQL 规范](docs/standards/mysql-sql.md)
- [外部数据源受控查询 SQL 规范](docs/standards/external-query-sql.md)
- [手工数据库初始化与升级](docs/standards/manual-database-upgrade.md)
- [SQL 变更检查清单](.agents/checklists/sql-change.md)
- [SQL 规范现状差距报告](docs/database/sql-standards-gap.md)

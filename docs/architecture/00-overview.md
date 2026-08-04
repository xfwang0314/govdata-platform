# 政务数据中台总体架构

- 状态：架构基线 v1（已确认）
- 适用范围：全仓库
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 目标

平台面向政务数据汇聚、治理、共享和服务，采用模块化单体优先、保留未来拆分能力的架构。
RuoYi-Vue-Plus 提供认证、组织、菜单、工作流和通用基础设施；自研平台层提供授权、
Secret、Event 和 MCP；政务数据领域层提供数据源、元数据、查询、交换、共享、服务、
标准、质量和本体能力。

## 分层

```text
ruoyi-*             脚手架和兼容层
platform-*          可复用的平台通用能力
govdata-*           政务数据领域能力
ruoyi-admin          单体运行时装配
```

目标模块结构见 [模块依赖](01-module-dependencies.md)。当前尚未完成的模块迁移和新增，
应在文档评审后分阶段实施，本文不代表代码已经具备全部能力。

## 核心架构原则

1. 每个领域拥有自己的数据、状态和实现，跨领域不得访问表、Mapper 或实现类。
2. 同步协作使用稳定契约；状态传播使用版本化事件和 Transactional Outbox。
3. 外部系统通过 Adapter 接入，领域接口不泄漏 SeaTunnel、Redis、JDBC 或 MCP 细节。
4. 租户代表独立地区，是硬隔离边界；不支持跨租户 Grant。
5. RBAC 管理功能入口，ABAC 管理具体 Resource 和行列约束，Grant 表达明确授权。
6. 在线外部数据读取统一经过 `govdata-query`，批量交换统一交给 SeaTunnel。
7. 所有凭据通过 SecretProvider 获取，所有数据访问和授权决策可追溯。

## 运行关系

```text
Client / MCP Client
        ↓
ruoyi-admin（认证、接口、统一装配）
        ↓
Application Interface
        ↓
Domain Module
        ├─ platform-auth
        ├─ govdata-query → Connector → 外部数据源
        ├─ govdata-exchange → SeaTunnel
        └─ Outbox → Redis Streams → 领域消费者
```

## 第一阶段边界

- 平台控制库只正式支持 MySQL 8，数据库脚本手工版本化维护。
- 采用 Redis Streams 作为跨模块事件传输，MySQL Outbox 是可靠事实记录。
- WarmFlow 管人工审批，嵌入式 Quartz 管后台调度，SeaTunnel 管数据交换执行。
- 旧 `data_scope` 仅用于 RuoYi 系统管理；新 `govdata-*` 模块统一使用 ABAC。
- 本体第一版只管理概念、关系和语义映射，不承载海量知识图谱实例。
- Agent/MCP 是后续能力，但身份、委托和授权 seam 从第一版设计中保留。

全模块控制库表的逻辑/物理设计入口见
[全模块数据库设计总览](../database/00-table-design-overview.md)。

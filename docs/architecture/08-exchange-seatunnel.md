# 数据交换与 SeaTunnel

- 状态：架构基线 v1（已确认）
- 适用范围：批量、增量和 CDC 数据交换
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 职责分离

`govdata-exchange` 是控制面，负责租户、权限、任务、映射、审批、发布、调度、状态和审计；
Apache SeaTunnel 是唯一交换执行引擎，负责 Source、Transform、Sink、并行、Checkpoint 和恢复。

## 核心模型

ExchangeJob、ExchangeJobVersion、ExchangePipeline、FieldMapping、SchedulePlan、ExchangeRun。
任务草稿可编辑，发布版本不可变；每次 ExchangeRun 绑定具体版本和 SeaTunnel Job ID。

## Engine seam

`ExchangeExecutionEngine` 暴露 submit、status、cancel 和必要的 savepoint/recovery 能力。
`SeaTunnelExecutionEngine` 隐藏 REST、认证、配置格式和版本差异。业务模型不得直接保存 HOCON。

## 流程

```text
编辑任务
→ 校验 Connector capability、Schema 和权限
→ 发布不可变版本
→ Quartz 触发运行
→ 编译 SeaTunnel 配置
→ 提交并同步状态
→ 保存指标与审计
```

Checkpoint 由 SeaTunnel 管理。平台不复制全部引擎内部状态，只保存业务运行摘要和外部作业引用。
字段映射引用稳定字段 ID；破坏性 Schema 变化暂停任务。凭据通过安全变量/临时解析注入，禁止写入
任务定义和日志。

---
status: accepted
scope: 数据交换
owner: GovData Platform Team
last_updated: 2026-08-01
---
# SeaTunnel 作为唯一交换执行引擎

`govdata-exchange` 只建设政务控制面和 SeaTunnel Adapter，不自研数据搬运、CDC、Checkpoint 或分布式执行。平台保留任务、权限、审批、发布、调度和审计，以复用成熟引擎可靠性并避免 SeaTunnel 模型侵入领域接口。

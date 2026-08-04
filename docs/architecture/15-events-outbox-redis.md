# 领域事件、Outbox 与 Redis Streams

- 状态：架构基线 v1（已确认）
- 适用范围：跨模块状态传播
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 模式

- 模块内部即时通知使用进程内事件。
- 跨模块可靠事件使用 MySQL Transactional Outbox + Redis Streams。
- Redis Pub/Sub 只用于允许丢失的缓存失效和实时刷新。

业务事务同时修改聚合并写 `outbox_event`。Publisher 提交后 XADD 到 Stream，模块通过独立
Consumer Group 消费，处理成功后 XACK。Outbox 保留一段时间用于对账和重放。

## 事件信封

```text
eventId, eventType, eventVersion, tenantId,
aggregateType, aggregateId, occurredAt, traceId, payload
```

事件名称使用过去式，payload 只包含稳定 ID、版本和必要摘要，不携带 Entity、密码或敏感原始数据。

## 可靠性

- 消费者以 eventId 幂等。
- 未确认消息通过 pending/claim 机制恢复；多次失败进入死信状态。
- Redis 开启合适持久化、监控和保留策略，但 MySQL Outbox 仍是可靠事实记录。
- 授权激活、撤销等高安全流程必须等待消费者确认后才更新业务最终状态。
- EventPublisher 是可替换 seam，未来可增加 Kafka Adapter 而不修改领域事件。


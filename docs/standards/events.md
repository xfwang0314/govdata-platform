# 领域事件规范

- 状态：v1（强制）
- 适用范围：进程内和跨模块事件
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

- 事件表达已经发生的事实，名称使用过去式。
- 跨模块事件使用统一信封：eventId、eventType、eventVersion、tenantId、aggregateType、
  aggregateId、occurredAt、traceId、payload。
- payload 只携带稳定 ID、版本和必要摘要，不携带 Entity、Secret 或敏感原值。
- 生产者在业务事务内写 Outbox；不得在事务内直接调用 Redis/外部服务并假设原子性。
- Redis Streams 消费者使用独立 Consumer Group、eventId 幂等和成功后 XACK。
- 事件 Schema 只做向后兼容增加；破坏性变化发布新 eventVersion。
- 多次失败进入死信并提供重放、告警和人工补偿。
- 授权激活/撤销等安全状态以消费者确认作为最终完成条件。
- 同步需要立即返回业务结果时使用契约调用，不滥用事件模拟 RPC。


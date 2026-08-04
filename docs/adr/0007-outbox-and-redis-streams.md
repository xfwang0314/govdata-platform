---
status: accepted
scope: 跨模块事件
owner: GovData Platform Team
last_updated: 2026-08-01
---
# MySQL Outbox 与 Redis Streams

跨模块可靠事件采用 MySQL Transactional Outbox 和 Redis Streams，普通 Redis Pub/Sub 只用于允许丢失的通知。Outbox 解决业务事务与 Redis 写入之间的原子性缺口，Redis Streams 提供消费组和重放；未来可通过 EventPublisher Adapter 替换 Kafka。

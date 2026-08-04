---
status: accepted
scope: 全局架构
owner: GovData Platform Team
last_updated: 2026-08-01
---
# 模块化单体优先

平台采用模块化单体并由 `ruoyi-admin` 统一装配，不在当前阶段引入微服务基础设施。领域按可拆分边界拥有数据和契约，跨模块默认通过接口或事件协作，使团队先获得边界和测试收益，同时避免远程调用、分布式事务和运维复杂度。

---
status: accepted
scope: 统一 Resource
owner: GovData Platform Team
last_updated: 2026-08-10
---
# Resource 是授权投影

Resource 只保存稳定标识和授权属性，各领域继续拥有完整业务实体。领域事务提交后发布投影事件，授权模块按来源模块、资源类型和业务稳定 ID 幂等更新；投影缺失时访问默认拒绝。该选择避免平台授权演变为统一业务大表，并允许不同领域独立演进；代价是需要事件重试、对账和可观测性。第一批投影为 `govdata:datasource`。

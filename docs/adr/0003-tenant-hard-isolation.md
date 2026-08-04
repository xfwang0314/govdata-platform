---
status: accepted
scope: 租户、用户与数据授权
owner: GovData Platform Team
last_updated: 2026-08-01
---
# 租户作为地区硬隔离边界

一个租户代表一个独立地区，账号单租户、Grant 不跨租户，平台管理员默认无租户业务数据权限。跨租户排障使用限时且强审计的 BreakGlass，而不是永久超级权限，以牺牲部分运维便利换取清晰的数据隔离责任。

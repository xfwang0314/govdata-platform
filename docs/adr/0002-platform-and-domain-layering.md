---
status: accepted
scope: Maven 模块与代码所有权
owner: GovData Platform Team
last_updated: 2026-08-01
---
# 分离脚手架、平台与领域层

仓库分为 `ruoyi-*`、`platform-*` 和 `govdata-*` 三层，并使用 `platform-api/platform-modules` 承载平台通用契约与实现。`platform-auth` 的契约和实现已分别迁移到这两个聚合模块。这样自研通用能力不会继续混入 RuoYi 模块，政务领域也不会反向污染平台能力。

# 模块与依赖架构

- 状态：架构基线 v1（目标结构）
- 适用范围：Maven 模块、Java 包依赖
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 目标结构

```text
ruoyi-common
ruoyi-api
ruoyi-modules

platform-api
platform-modules
├─ platform-auth
├─ platform-secret
├─ platform-event
└─ platform-mcp

govdata-api
govdata-modules
├─ govdata-datasource
├─ govdata-metadata
├─ govdata-query
├─ govdata-exchange
├─ govdata-sharing
├─ govdata-serving
├─ govdata-standard
├─ govdata-quality
└─ govdata-ontology
```

## 允许方向

```text
ruoyi-*        ← platform-* ← govdata-*
     ↑               ↑           ↑
     └──────── ruoyi-admin 装配 ─┘
```

- `ruoyi-api` 只承载脚手架契约。
- `platform-api` 承载自研通用平台契约，可依赖最小 `ruoyi-common`。
- `govdata-api` 承载领域跨模块契约，可依赖 `platform-api` 和必要 `ruoyi-api`。
- 实现模块只依赖实际使用的契约和基础模块。
- `ruoyi-admin` 是组合根，负责选择和装配实现。

## 禁止方向

- `ruoyi-api → platform-api/govdata-api`。
- `platform-api → ruoyi-api/govdata-api/任何实现`。
- `govdata-api → platform-modules/govdata-modules`。
- `platform-modules → govdata 实现`。
- 一个 `govdata-*` 实现模块直接依赖另一个领域的 Mapper、Entity 或 ServiceImpl。
- 父级聚合 POM 注入业务依赖。

## 跨模块 seam

只有两个以上调用者或真实可替换 Adapter 需要共同使用的稳定能力才提取契约。契约参数应使用
Command、Query、View 和稳定 ID；不得暴露持久化 Entity、Page、Wrapper、HttpServletRequest。

## 验证

- Maven Enforcer 检查 artifact 禁止方向和未声明依赖。
- ArchUnit 检查 Java 包依赖以及 Domain 对框架的隔离。
- 每个子模块支持独立 `compile/test`。
- 目标结构迁移前，当前代码与本文的差异应列入实施计划而不是隐藏。


# Git 工作流规范

- 状态：v1（强制）
- 适用范围：分支、提交、合并和发布
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

- `main` 保持可构建和可部署，不直接开发。
- 使用短期 `feature/*`、`refactor/*`、`fix/*`、`docs/*` 分支，不维护长期 develop。

## Commit Message 规范

提交必须符合 Conventional Commits 标准。格式如下：

`<type>(<scope>): <subject>`

- **type** (必需): 变更类型，可选值 `feat` (新功能), `refactor` (重构), `fix` (修复), `docs` (文档), `test` (测试), `build` (构建), `chore` (日常事务)。
- **scope** (必需): 影响范围，按技术或模块划分。目前约定的 scope 有：`scheduler` (调度), `db` (数据库/SQL), `docs` (文档/ADR), `infra` (基础配置), `platform` (platform-* 模块), `govdata` (govdata-* 模块)。
- **subject** (必需): 简短的中文描述，总结变更内容。

示例：`chore(scheduler): 移除 SnailJob`

- 一个提交表达一个完整意图。
- 代码、测试、必要 SQL 和文档随同一功能提交链更新。
- 禁止提交 target、日志、IDE 缓存、本地配置和 Secret。
- 合并前同步 main，执行模块测试、架构规则和必要集成测试。
- 禁止强推覆盖共享分支；已发布 SQL 和稳定接口不得通过改历史修正。
- 未跟踪文件先确认所有者和用途，不能为了工作区干净随意纳入或删除。
- 大型基础改造按主题拆分可审查提交，不压成单一巨型提交。


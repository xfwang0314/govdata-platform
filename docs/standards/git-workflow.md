# Git 工作流规范

- 状态：v1（强制）
- 适用范围：分支、提交、合并和发布
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 分支命名规范

- `main` 保持可构建和可部署，不直接开发。
- 使用短期功能分支进行开发，禁止维护长期的 `develop` 分支。
- **分支前缀约定**（与 Commit Type 对应）：
  - `feature/xxx` 或 `feat/xxx`：开发新业务功能（如 `feature/datasource`）。
  - `fix/xxx`：修复代码 Bug（如 `fix/login-null-pointer`）。
  - `refactor/xxx`：代码重构，不改变已有逻辑功能（如 `refactor/auth-service`）。
  - `chore/xxx`：杂项、基础设施调整、依赖更新、脚手架清理等非业务代码的改动（如 `chore/framework-init`）。
  - `docs/xxx`：纯文档编写与修改。

## Commit Message 规范

提交必须符合 Conventional Commits（语义化提交）标准。格式如下：

`<type>(<scope>): <subject>`

- **type** (必需): 变更类型，请严格区分适用场景：
  - `feat`: ✨ 新增功能（如添加新接口、新页面）。
  - `fix`: 🐛 修复 Bug。
  - `chore`: 🔧 杂项事务（清理冗余代码、更新配置文件、调整构建脚本等，完全不影响业务功能）。
  - `refactor`: ♻️ 重构（既不是新增功能，也不是修改 bug 的代码变动，如重命名变量、提取公用方法）。
  - `docs`: 📝 纯文档修改（如修改 README、补充 ADR）。
  - `test`: ✅ 增加缺失的测试代码。
  - `build`: 📦 影响构建系统或外部依赖的更改（如修改 pom.xml 或 package.json）。
  - `perf`: ⚡️ 提升性能的代码修改。
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


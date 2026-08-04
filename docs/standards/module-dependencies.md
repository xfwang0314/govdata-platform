# Maven 与模块依赖规范

- 状态：v1（强制）
- 适用范围：所有 POM 和 Java 模块
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

- 聚合 POM 只声明 modules、插件和构建约定，不注入业务 dependencies。
- dependencyManagement 只管理版本，不代表已引入依赖。
- 子模块显式声明直接使用的依赖，不依赖偶然的传递依赖。
- `test`、`optional` 和 runtime scope 必须准确标记。
- 禁止创建一次引入全部 common 的超级 starter。
- 契约模块不得依赖实现；实现模块不得反向污染契约。
- `ruoyi-* → platform-*/govdata-*`、`platform-* → govdata 实现` 等禁止方向由 Enforcer 检查。
- Java 包依赖和 Domain 框架隔离由 ArchUnit 检查。
- 新模块必须能独立 compile/test，并在 README 或模块说明中声明职责、Interface、数据所有权和事件。
- Maven Wrapper 是团队构建入口，JDK 固定为 17；不得以本机 Maven 差异解释 CI 失败。

目标依赖图见 [模块架构](../architecture/01-module-dependencies.md)。


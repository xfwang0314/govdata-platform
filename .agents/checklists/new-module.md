# 新模块检查清单

- [ ] 模块职责、拥有的数据和外部 Interface 已写入架构文档。
- [ ] 包名属于 `com.xfwang.platform.*` 或 `com.xfwang.govdata.*`。
- [ ] 聚合 POM 只添加 module，子模块显式声明直接依赖。
- [ ] 未依赖其他领域的 Entity、Mapper、ServiceImpl 或业务表。
- [ ] 跨模块契约只在确有调用者时放入 platform-api/govdata-api。
- [ ] Domain 不依赖 Spring、MyBatis、Sa-Token。
- [ ] 已添加 Maven Enforcer/ArchUnit 约束和模块测试。
- [ ] 已更新 CONTEXT-MAP、领域 CONTEXT 和必要 ADR。


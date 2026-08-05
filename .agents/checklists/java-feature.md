# Java 功能开发清单

## 开发前

- [ ] 已阅读 `CONTEXT-MAP.md`、目标领域 `CONTEXT.md`、`docs/standards/java-coding.md` 和
      `docs/standards/java-layering.md`。
- [ ] 已确认能力归属的领域模块，没有新增跨模块数据库访问。
- [ ] 已明确用例、RBAC 权限、ABAC/Grant 资源动作和事务边界。
- [ ] 已识别外部调用、敏感数据、稳定状态、ErrorCode 和事件需求。

## 设计与实现

- [ ] 已先定义 Command、Query、View 或跨模块稳定契约，再实现 Controller。
- [ ] 创建与修改规则不同的用例没有复用万能可空 Command。
- [ ] 已同步设计 SQL、`domain.entity` 和稳定枚举；Entity 未进入接口或事件。
- [ ] ApplicationService 按相关用例组拆分，没有模块总管类或无意义透传接口。
- [ ] Entity 自身不变量、跨对象纯规则和 Application 编排职责清晰。
- [ ] 普通 CRUD 直接使用本模块 Mapper；只有真实复杂存储才创建 Repository。
- [ ] 外部调用位于数据库事务外，结果通过短事务记录。
- [ ] Controller 只做协议适配和 RBAC；Application 执行租户及 ABAC/Grant。
- [ ] 凭据只通过 `secretRef`/`SecretProvider` 使用，日志和 View 不包含敏感内容。

## 验证

- [ ] 领域规则有纯单元测试，Application 用例有成功和失败路径测试。
- [ ] 自定义 SQL、JSON、租户、乐观锁或复杂分页有 MySQL 8 集成测试。
- [ ] 主要 HTTP 接口验证 `R<T>`、校验、权限、ErrorCode、traceId 和序列化。
- [ ] 已执行目标模块测试、ArchUnit、Maven Enforcer 和必要集成测试。
- [ ] 已同步更新 SQL、领域文档、接口说明和迁移说明。
- [ ] 未跳过测试、未修改无关文件、未自动提交或推送。

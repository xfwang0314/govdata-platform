# govdata-datasource 模块上下文

本模块拥有外部数据源实例、非敏感端点配置、SecretRef 和连接测试事实。

## 边界

- 对外提供数据源登记、查询、修改、停用和受授权的连接测试入口。
- 创建数据源时向 `platform-auth` 注册 `govdata:data-source` Resource 投影。
- Connector 按能力扩展；禁止按产品类型编写集中式 `switch`。
- 不保存凭据原文，不拥有元数据、交换任务、查询 SQL 或质量规则。
- 其他领域只能通过稳定契约或事件引用 `datasourceId`，不得访问本模块 Mapper 和业务表。

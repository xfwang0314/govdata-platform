# govdata-datasource 模块上下文

本模块拥有外部数据源实例、非敏感端点配置、SecretRef 和连接测试事实。

## 边界

- 对外提供数据源登记、查询、修改、停用和受授权的连接测试入口。
- 创建数据源时向 `platform-auth` 注册 `govdata:data-source` Resource 投影。
- Connector 按能力扩展；禁止按产品类型编写集中式 `switch`。
- 不保存凭据原文，不拥有元数据、交换任务、查询 SQL 或质量规则。
- 其他领域只能通过稳定契约或事件引用 `datasourceId`，不得访问本模块 Mapper 和业务表。
- 数据源编码由后端生成并保持稳定；Connector 版本由 Catalog 决定，页面不得提交。
- 新建或修改连接配置后状态为 `DISABLED`，只有最近一次连接测试成功的数据源才能启用。
- 数据源停用不等于授权 Resource 停用，否则无法授权连接测试；Resource 仅在数据源删除时归档。
- 固定选项由本模块 options Interface 提供，Connector 动态字段由 Catalog Interface 提供。

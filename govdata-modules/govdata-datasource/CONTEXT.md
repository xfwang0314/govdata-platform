# govdata-datasource 模块上下文

本模块拥有外部数据源实例、非敏感端点配置、SecretRef 和连接测试事实。

## 边界

- 对外提供数据源登记、查询、修改、停用和受授权的连接测试入口。
- 数据源创建、修改、启停后通过提交后事件幂等投影为 `govdata:datasource` Resource；删除后归档投影。
- 详情、修改、启停、删除、连接测试和元数据探测执行对象级 ABAC；Sa-Token 仍负责接口功能准入。
- Resource 投影只保存权属部门、分类、安全等级和已注册连接属性，不复制端点配置或 SecretRef。
- 投影缺失时对象级访问默认拒绝；不得绕过授权直接访问本模块 Mapper。
- 列表查询在数据库分页前通过 `AuthorizedResourceService` 获取当前主体具备 `VIEW` 动作的资源集合，
  再按 `authz_resource_id` 过滤；空集合直接返回空页，禁止分页后内存过滤。
- 新建仍由 RBAC 控制功能入口，因为对象尚未产生；详情、修改、启停、删除、连接测试、测试历史和元数据
  检查均对具体 Resource 执行 ABAC/Grant 决策。
- Connector 按能力扩展；禁止按产品类型编写集中式 `switch`。
- 不保存凭据原文，不拥有元数据、交换任务、查询 SQL 或质量规则。
- 其他领域只能通过稳定契约或事件引用 `datasourceId`，不得访问本模块 Mapper 和业务表。
- 数据源编码由后端生成并保持稳定；Connector 版本由 Catalog 决定，页面不得提交。
- 新建或修改连接配置后状态为 `DISABLED`，只有最近一次连接测试成功的数据源才能启用。
- 第一版提供 MySQL、Oracle、PostgreSQL、ClickHouse 四种 JDBC Connector，以及 Redis 单机、HTTP API、SFTP Connector。
- 页面只提交主机、端口、数据库/服务名等 Catalog 基础字段与非敏感连接属性；连接目标由后端生成。
- 可选连接预览、字段 Schema、认证方式和凭据契约均由 Connector Catalog 提供，前端不识别具体协议。
- Connector 的凭据类型使用 `platform-api.SecretType`，不得复制类型编码和最小必需字段；认证方案编码仍由对应 Adapter 拥有。
- 数据源凭据用途由 `DataSourceSecretUsage` 拥有，并通过 `DataSourceSecretUsageContributor` 注册给平台凭据目录；
  `platform-secret` 不得硬编码数据源模块用途。
- Redis 第一版支持 Redis 6+ ACL 用户名密码或传统仅密码认证、`redis/rediss` 和逻辑库 `0–15`，
  不支持 Sentinel/Cluster 和无认证连接。
- HTTP API 支持无认证、Basic、Bearer、API Key Header 与 OAuth2 Client Credentials；生产环境强制 HTTPS，
  禁止重定向并执行 SSRF 目标校验。OAuth2 使用 Redis 短期缓存和分布式刷新锁，401 最多刷新重试一次。
- SFTP 支持密码和 SSH 私钥认证，必须严格校验 SHA256 主机密钥指纹。
- 连接异常必须通过统一分类器输出稳定原因码，不得向页面或日志返回驱动异常原文。
- 固定选项由本模块 options Interface 提供，前端不得硬编码状态、分类和安全等级等业务值。

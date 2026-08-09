# 数据源

- 状态：P0 后端已实现，等待页面联调
- 适用范围：数据源上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-05

数据源上下文管理外部系统连接实例及其能力，不拥有技术元数据或数据交换任务。

## Language

**Data Source Instance**:
租户内一个可连接外部系统的逻辑配置，引用 Connector 和 Secret。
_Avoid_: 数据库、连接池本身

**Connector**:
适配一种外部协议或产品并声明可用能力的实现。
_Avoid_: 驱动类型 switch

**Capability**:
Connector 可提供的独立能力，如连通测试、元数据发现、查询预览或 SeaTunnel 配置转换。
_Avoid_: 数据源类型

**Secret Reference**:
指向受保护凭据的稳定引用，业务配置不得包含凭据原文。
_Avoid_: password 字段

**Access Mode**:
数据源凭据被允许承担的访问方式，第一版为只读或读写；它是能力和授权约束，不替代外部系统账号权限。
_Avoid_: 仅用于页面展示的只读标记

**Data Classification**:
平台内部稳定的数据敏感分类，第一版为公开、内部、敏感、受限；不等同于国家秘密等级。
_Avoid_: 可任意维护的展示字典

## 第一版实现

- `govdata-datasource` 拥有 `gd_datasource` 和 `gd_datasource_connection_test`。
- 页面功能权限由 Sa-Token 校验。数据源功能完成前暂不创建授权 Resource，也不执行数据源 ABAC/Grant 决策；
  `authz_resource_id` 保留为空，后续统一开启投影注册。
- 第一版内置 MySQL、Oracle、PostgreSQL、ClickHouse 四种 JDBC `ConnectionTester`，以及 Redis 单机、
  HTTP API 和 SFTP `ConnectionTester`；平台控制库仍只支持 MySQL 8。
- 页面通过 Connector Catalog 获取基础连接字段、可选连接预览、认证方式和能力，不自行维护 Connector 版本。
- `connectionPreview` 使用通用的标签和模板描述非敏感连接目标；JDBC、Redis、SFTP 等协议不得要求页面识别产品差异。
- 用户只填写主机、端口、数据库/服务名等基础字段；页面只读预览连接目标，后端独立生成并以
  `connectionTarget` 持久化。JDBC Adapter 临时兼容历史 `jdbcUrl` 数据，但新数据不得继续写入该字段。
- Connector 显式声明是否允许自定义非敏感连接属性；基础字段和属性均禁止携带凭据等敏感值。
- JDBC 使用用户名密码认证；Redis 第一版同时支持 Redis 6+ ACL 用户名密码和传统仅密码认证，支持
  `redis/rediss`、单机模式和逻辑库 `0–15`，暂不支持 Sentinel、Cluster 和无认证连接。只允许选择认证方式
  对应的凭据类型、用途包含
  `DATASOURCE_CONNECTION_TEST` 且具有 `username`、`password` 的有效凭据。
- HTTP API 支持 `NONE`、Basic、Bearer Token、API Key Header 与 OAuth2 Client Credentials；无认证时
  `secret_ref` 必须为空，其他方式必须选择匹配类型的凭据。OAuth2 令牌仅短期缓存于 Redis，401 时最多刷新重试一次。
- SFTP 支持用户名密码与 SSH 私钥，必须保存并验证服务端 SHA256 主机密钥指纹，不接受“信任所有主机”。
- 凭据类型及最小字段统一使用 `platform-api.SecretType`；数据源模块拥有 `DataSourceSecretUsage`，并通过
  `DataSourceSecretUsageContributor` 向平台凭据目录贡献用途，不在 `platform-secret` 复制数据源用途编码。
- 数据源编码由服务端生成且创建后不可修改；Connector 编码由页面选择，Connector 版本由 Catalog 回填。
- 环境、访问模式、数据分类、安全等级和状态由服务端固定选项提供，不以可编辑字典作为事实来源。
- 新建和修改连接配置后数据源保持停用，连接测试成功后才能显式启用。
- 数据源支持启用、停用和逻辑删除；删除前必须先停用。授权投影启用后的生命周期另行评审。
- 每次连接测试保存脱敏结果和稳定原因码，页面可以分页查询测试历史。
- 外部异常统一映射为认证失败、数据库不存在、连接超时、拒绝连接、主机不可达、TLS 握手失败、驱动不可用等
  稳定原因码；响应、日志和测试记录不得保存驱动异常原文。
- 凭据解析统一由 `platform-secret` 提供的 `SecretProvider`（`secret:sec_xxx`）负责，执行多租户与审计管控。

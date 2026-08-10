# 数据源与 Connector 架构

- 状态：架构基线 v1（P0 已实现）
- 适用范围：第一版 MySQL、Oracle、PostgreSQL、ClickHouse、Redis、HTTP API、SFTP；后续扩展其他外部系统
- 所有者：GovData Platform Team
- 最后更新：2026-08-05

## 职责

`govdata-datasource` 管理数据源实例、连接配置、SecretRef、连通测试和 Connector 调度，
不拥有技术元数据、交换任务或质量规则。

## 能力型 SPI

Connector 声明自己支持的能力，而不是实现一个大而全接口：

```text
ConnectionTester
MetadataDiscoverer
QueryPreviewer
CapabilityDescriptor
SeaTunnelConfigAdapter
```

业务模块按 capability 调用，不按数据源类型写 `switch`。JDBC 可共享基础实现，但 Redis、
SFTP 不伪装成 JDBC。高吞吐 Source、Sink、CDC、Checkpoint 使用 SeaTunnel Connector，
平台不重复实现 DataReader/DataWriter。

## 配置与凭据

- 数据源业务配置保存基础 endpoint 字段、系统生成的 `connectionTarget`、非敏感连接属性和 `secretRef`。
- 页面不得提交连接目标；Catalog 根据 Connector 字段 Schema 校验主机、端口和数据库/服务名，
  再使用可选的 `connectionPreview` 模板生成可信连接目标。页面根据同一模板提供只读预览，
  但不作为后端事实来源。
- Connector Catalog 声明认证方式、`secretType` 和必要 Secret keys；页面按 Connector 与认证方式从
  `/govdata/datasources/secret-references` 获取当前租户可选引用，后端保存时再次校验。
- Connector 的通用字段约束由 Catalog 执行，协议相关组合约束由对应 `ConnectorEndpointValidator` 执行；
  禁止在统一管理服务中按 Connector 编码堆叠条件分支。
- SecretProvider 在建立连接前按需解析凭据，响应和日志不回显。
- Connector 统一管理连接池、超时、并发、资源释放和异常映射。
- 测试连接也必须经过租户、权限和审计。

## 扩展流程

新增 Connector 必须提供能力描述、配置 Schema、Adapter、合同测试和运维说明。SeaTunnel 已支持
的数据交换能力优先直接复用；缺失能力优先贡献/开发 SeaTunnel 插件，而不是分叉第二套执行引擎。

## P0 运行链路

```text
DataSourceController
  -> Sa-Token 功能权限
  -> DataSourceManagementService
  -> 暂仅执行 Sa-Token 功能权限
  -> ConnectionTesterRegistry
  -> 对应的 ConnectionTester Adapter
  -> SecretProvider.resolve(SecretResolveRequest)
  -> 写入脱敏测试记录
```

业务代码只依赖 `ConnectionTester` 能力，不感知 Connector 查找细节。第一版提供 MySQL、Oracle、
PostgreSQL、ClickHouse、Redis、HTTP API 和 SFTP Adapter，统一接受 Catalog 声明的基础连接字段；只有显式声明支持的
Connector 才能接收非敏感自定义连接属性。Catalog 负责
生成 `connectionTarget` 并拒绝客户端提交连接目标或在属性中夹带敏感字段；Connector 自己决定预览标签，
因此 JDBC 显示“JDBC URL”，Redis、SFTP 可以显示各自的连接地址。连接异常原文、连接目标和 SecretValue
均不得进入日志。

Redis 第一版支持单机 ACL 或传统仅密码认证，不包含 Sentinel/Cluster 和数据交换执行。HTTP API 支持无认证、
Basic、Bearer、API Key Header 和 OAuth2 Client Credentials；生产环境只允许 HTTPS，不跟随重定向，目标地址需经过
SSRF 校验。OAuth2 令牌使用 Redis 短期缓存和分布式刷新锁，业务请求遇到 401 最多强制刷新并重试一次。
SFTP 支持密码与 SSH 私钥认证，必须配置并严格校验 SHA256 主机密钥指纹。Doris 可按 MySQL 协议复用能力，
SQL Server 作为下一批 JDBC Connector；这些扩展不得通过在现有 JDBC
表单中堆叠产品分支实现。数据源功能验收后再恢复 Resource
投影注册和 ABAC/Grant 授权链路。

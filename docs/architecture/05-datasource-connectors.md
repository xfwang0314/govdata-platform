# 数据源与 Connector 架构

- 状态：架构基线 v1（P0 已实现）
- 适用范围：MySQL、Oracle、Redis、SFTP、Doris、ClickHouse 等外部系统
- 所有者：GovData Platform Team
- 最后更新：2026-08-03

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

- 数据源业务配置只保存 endpoint 配置和 `secretRef`。
- Connector Catalog 同时声明 `secretType` 和必要 Secret keys；页面按类型从
  `/govdata/datasources/secret-references` 获取当前租户可选引用，不接触 SecretValue。
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
  -> DataSourceAuthorizedOperations
  -> @ResourceAuthorize(TEST_CONNECTION)
  -> platform-auth 决策
  -> ConnectionTesterRegistry
  -> 对应的 ConnectionTester Adapter
  -> SecretProvider.resolve(SecretResolveRequest)
  -> 写入脱敏测试记录
```

业务代码只依赖 `ConnectionTester` 能力，不感知 Connector 查找细节。第一版提供 MySQL、Oracle、
Redis、SFTP、Doris、ClickHouse Adapter；它们只接受 Catalog 声明的结构化配置，不允许页面提交任意
JDBC URL。SFTP 必须校验服务端 SHA256 主机密钥指纹。连接异常被映射为稳定原因码，驱动异常、连接串
和 SecretValue 均不得进入响应或日志。

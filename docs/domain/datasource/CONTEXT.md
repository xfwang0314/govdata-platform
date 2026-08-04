# 数据源

- 状态：P0 后端已实现，等待页面联调
- 适用范围：数据源上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-03

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

## 第一版实现

- `govdata-datasource` 拥有 `gd_datasource` 和 `gd_datasource_connection_test`。
- 创建数据源实例时同步注册 `govdata:data-source` 授权 Resource，并保存其稳定 ID。
- 页面功能权限由 Sa-Token 校验；连接测试另由 `@ResourceAuthorize` 执行 Resource + ABAC/Grant 决策。
- 第一版内置 MySQL、Oracle、Redis、SFTP、Doris、ClickHouse 六种 `ConnectionTester`；平台控制库仍只支持 MySQL 8。
- 页面通过 Connector Catalog 获取字段、默认值和能力，不自行维护 Connector 版本或任意 JDBC URL。
- 数据源支持启用、停用和逻辑删除；删除前必须先停用，状态同步到授权 Resource。
- 每次连接测试保存脱敏结果和稳定原因码，页面可以分页查询测试历史。
- 第一阶段提供 `env:用户名变量,密码变量` SecretProvider，后续可由 platform-secret Adapter 替换或扩展。

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

**Access Mode**:
数据源凭据被允许承担的访问方式，第一版为只读或读写；它是能力和授权约束，不替代外部系统账号权限。
_Avoid_: 仅用于页面展示的只读标记

**Data Classification**:
平台内部稳定的数据敏感分类，第一版为公开、内部、敏感、受限；不等同于国家秘密等级。
_Avoid_: 可任意维护的展示字典

## 第一版实现

- `govdata-datasource` 拥有 `gd_datasource` 和 `gd_datasource_connection_test`。
- 创建数据源实例时同步注册 `govdata:data-source` 授权 Resource，并保存其稳定 ID。
- 页面功能权限由 Sa-Token 校验；连接测试另由 `@ResourceAuthorize` 执行 Resource + ABAC/Grant 决策。
- 第一版内置 MySQL、Oracle、Redis、SFTP、Doris、ClickHouse 六种 `ConnectionTester`；平台控制库仍只支持 MySQL 8。
- 页面通过 Connector Catalog 获取字段、默认值和能力，不自行维护 Connector 版本或任意 JDBC URL。
- 数据源编码由服务端生成且创建后不可修改；Connector 编码由页面选择，Connector 版本由 Catalog 回填。
- 环境、访问模式、数据分类、安全等级和状态由服务端固定选项提供，不以可编辑字典作为事实来源。
- 新建和修改连接配置后数据源保持停用，连接测试成功后才能显式启用。
- 数据源业务状态不等于授权 Resource 生命周期：对象存在期间 Resource 保持可授权，
  `datasourceStatus` 作为属性参与 ABAC；仅删除数据源时归档 Resource。
- 数据源支持启用、停用和逻辑删除；删除前必须先停用，状态同步到授权 Resource。
- 每次连接测试保存脱敏结果和稳定原因码，页面可以分页查询测试历史。
- `platform-secret` 提供数据库密文 SecretProvider；`env:` Adapter 仅用于本地开发和部署注入。

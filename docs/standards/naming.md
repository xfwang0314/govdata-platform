# 命名规范

- 状态：v1（强制）
- 适用范围：模块、Java、接口、权限、Resource、事件、MCP、数据库
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 稳定命名

- Maven artifact 使用小写连字符：`govdata-metadata`。
- Java 包使用小写领域名：`com.xfwang.govdata.metadata`。
- REST 使用复数名词：`/govdata/metadata/tables`。
- RBAC 权限使用 `govdata:{domain}:{resource}:{action}`。
- Resource 类型使用命名空间：`govdata:metadata:table`。
- 领域事件使用过去式：`MetadataSchemaChanged`。
- MCP Tool 使用小写蛇形：`quality_check_run`。
- 数据库使用小写蛇形并按领域前缀。

接口路径、RBAC 权限、Resource 类型、事件和 Tool 各自使用独立稳定编码，不直接复用字符串。
展示名称可以修改，稳定编码发布后不得复用或无兼容期重命名。

## Java 后缀

使用 `Command`、`Query`、`View`、`ApplicationService`、`Repository`、`Adapter`、`Event` 表达角色。
禁止 `DsMgrSvc`、`MetaTblDTO` 等含糊缩写。API、SQL、JDBC、MCP、URL、ID、CDC、SFTP 属于允许缩写。

术语以 [CONTEXT-MAP](../../CONTEXT-MAP.md) 为准；同一概念不得在不同模块使用不同名称。


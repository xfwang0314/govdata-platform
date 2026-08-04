# platform-auth 与数据源页面联调

当前后端仓库不包含 Vue 前端源码，第一轮使用 Swagger UI 作为可视化联调页面。正式数据源管理页接入时复用同一组接口，不能绕过授权入口。

## 准备

1. 新库执行 `script/sql/ry_auth.sql` 和 `script/sql/ry_datasource.sql`；已有库按版本顺序执行 `script/sql/upgrade`，本轮至少执行 `20260803_001`、`20260803_003`。
2. 为要测试的外部数据源设置凭据环境变量，例如 `GOV_DS_USER`、`GOV_DS_PASSWORD`，重启后端进程。
3. 启动后打开 `http://localhost:8080/swagger-ui/index.html`，使用登录接口返回的 Token 完成 Swagger 授权。

## 创建数据源

先调用 `GET /govdata/datasources/connectors` 获取六类 Connector、版本、能力及表单字段。页面不得写死
`connectorVersion`，创建时后端按 Connector Catalog 保存当前版本。随后调用 `POST /govdata/datasources`：

```json
{
  "datasourceCode": "local_mysql",
  "datasourceName": "本地 MySQL",
  "connectorCode": "mysql",
  "endpointConfig": {
    "host": "127.0.0.1",
    "port": 3306,
    "database": "ry-vue"
  },
  "secretRef": "env:GOV_DS_USER,GOV_DS_PASSWORD",
  "environment": "DEV",
  "ownerDeptId": 103,
  "readonlyFlag": true,
  "classification": "INTERNAL",
  "securityLevel": 1
}
```

响应中的 `authzResourceId` 是自动注册的授权资源 ID。数据库配置和响应都不会出现用户名或密码值。

## 验证拒绝与放行

1. 尚未配置 Policy 或 Grant 时调用 `POST /govdata/datasources/{datasourceId}/connection-tests?purpose=开发联调`，预期被 ABAC 默认拒绝，并产生授权决策审计。
2. 在“5.平台授权模块”创建并激活一个 Grant：`granteeType=HUMAN`、`granteeId=当前登录用户ID`，scope 使用上一步的 `authzResourceId`，`actionCode=TEST_CONNECTION`，用途与测试用途保持一致。
3. 先调用 `POST /platform/auth/decisions/evaluate` 验证决策为 `PERMIT`，再重新执行连接测试。
4. 连接成功或失败都应写入 `gd_datasource_connection_test`；响应仅包含稳定原因码、耗时和脱敏服务端信息。

Sa-Token 的 `govdata:datasource:test` 是“能否使用连接测试功能”，Resource + ABAC/Grant 是“能否对这个具体数据源执行测试”，两层均必须通过。

## 页面接口约定

- 列表、详情、新增、修改：`GET/POST/PUT /govdata/datasources`
- 启用、停用、删除：`PUT .../{id}/enable`、`PUT .../{id}/disable`、`DELETE .../{id}`
- 连接测试与历史：`POST/GET .../{id}/connection-tests`
- 前端只提交 `secretRef`，不得提交、缓存或回显用户名和密码。
- 更新请求必须回传详情中的 `lockVersion`；冲突时刷新详情后再编辑。

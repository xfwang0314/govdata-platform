# 平台授权与数据源 API 冒烟测试

- 状态：当前可执行
- 适用范围：`platform-auth`、`platform-secret`、`govdata-datasource` 的 Swagger API 联调
- 所有者：GovData Platform Team
- 最后更新：2026-08-21

当前后端仓库不包含 Vue 前端源码，第一轮使用 Swagger UI 作为可视化联调页面。正式数据源管理页接入时复用同一组接口，不能绕过授权入口。

## 前置条件

1. 当前开发库删除旧表后按 `script/sql/baseline/manifest.md` 执行
   `20_platform_auth.sql`、`21_platform_secret.sql` 和 `30_govdata_datasource.sql`；
   本轮不生成历史数据迁移语句。
2. 设置 `PLATFORM_SECRET_MASTER_KEY`（Base64 编码的 32 字节随机值），重启后端进程。
3. 启动后打开 `http://localhost:8080/swagger-ui/index.html`，使用登录接口返回的 Token 完成 Swagger 授权。

## 创建数据源

先通过 `POST /platform/secrets` 创建 `ownerModule=govdata-datasource`、
`purposeScope=DATASOURCE_CONNECTION_TEST` 的 `USERNAME_PASSWORD` 凭据。接口只返回 SecretRef，不回显值。
再调用 `GET /govdata/datasources/connectors` 获取六类 Connector、版本、能力、凭据类型及表单字段，调用
`GET /govdata/datasources/options` 获取所有固定选项。页面不得写死这些编码，也不得提交 Connector 版本。
随后调用 `POST /govdata/datasources`：

```json
{
  "datasourceName": "本地 MySQL",
  "connectorCode": "mysql",
  "endpointConfig": {
    "host": "127.0.0.1",
    "port": 3306,
    "database": "ry-vue"
  },
  "secretRef": "secret:sec_xxx",
  "environment": "DEV",
  "ownerDeptId": 103,
  "accessMode": "READ_ONLY",
  "classificationCode": "INTERNAL",
  "securityLevel": 1
}
```

`datasourceCode`、`connectorVersion` 和 `authzResourceId` 均由后端生成。新建数据源状态为 `DISABLED`；
数据库配置和响应都不会出现用户名或密码值。

## 验证拒绝与放行

1. 尚未配置 Policy 或 Grant 时调用 `POST /govdata/datasources/{datasourceId}/connection-tests?purpose=开发联调`，预期被 ABAC 默认拒绝，并产生授权决策审计。
2. 在平台授权模块创建并激活一个 Grant：`granteeType=HUMAN`、`granteeId=当前登录用户 ID`，scope 使用上一步的 `authzResourceId`，`actionCode=TEST_CONNECTION`，用途与测试用途保持一致。
3. 先调用 `POST /platform/auth/decisions/evaluate` 验证决策为 `PERMIT`，再重新执行连接测试。
4. 连接成功或失败都应写入 `gd_datasource_connection_test`；响应仅包含稳定原因码、耗时和脱敏服务端信息。
5. 最近一次连接测试成功后调用 `PUT /govdata/datasources/{id}/enable`；修改连接配置后会自动停用并要求重新测试。

Sa-Token 的 `govdata:datasource:test` 是“能否使用连接测试功能”，Resource + ABAC/Grant 是“能否对这个具体数据源执行测试”，两层均必须通过。

## 页面接口约定

- 列表、详情、新增、修改：`GET/POST/PUT /govdata/datasources`
- 启用、停用、删除：`PUT .../{id}/enable`、`PUT .../{id}/disable`、`DELETE .../{id}`
- 连接测试与历史：`POST/GET .../{id}/connection-tests`
- 前端只提交 `secretRef`，不得提交、缓存或回显用户名和密码。
- 可选凭据引用通过 `GET /govdata/datasources/secret-references?secretType=USERNAME_PASSWORD` 获取。
- 更新请求必须回传详情中的 `lockVersion`；冲突时刷新详情后再编辑。

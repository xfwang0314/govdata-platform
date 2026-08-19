# Platform Auth 授权闭环验证

- 状态：开发环境验证手册
- 适用范围：`platform-modules/platform-auth`
- 数据库：MySQL 8.0
- 最后更新：2026-08-13

## 验证目标

按顺序验证以下闭环：

```text
登录与功能权限（Sa-Token）
→ 注册 Resource
→ 默认拒绝
→ 发布 ABAC Policy 后允许
→ 创建并激活 Grant 后允许
→ 撤销 Grant 后恢复拒绝
→ 检查授权决策审计
```

可以使用授权管理页面完成同一闭环；本手册保留接口方式，便于排除前端因素并复现问题。

## 准备数据库与服务

1. `script/sql/baseline/20_platform_auth.sql` 是空库或可丢弃开发库的授权基线，会删除并重建授权表。禁止直接在已有数据的环境执行。
2. 已有运行数据的环境必须先备份，并等待专用升级脚本评审后再迁移。
3. 启动 MySQL、Redis 和后端，使用超级管理员登录。
4. 打开 `http://localhost:8080/swagger-ui/index.html`，在右上角分组中选择
   “5.平台授权模块”。项目使用 SpringDoc Swagger UI，不提供 Knife4j 的 `/doc.html`。

普通角色调试时，需要按实际操作授予以下 Sa-Token 功能权限：

```text
platform:auth:resource:add
platform:auth:resource:query
platform:auth:policy:add
platform:auth:policy:publish
platform:auth:policy:query
platform:auth:policy:edit
platform:auth:policy:disable
platform:auth:grant:add
platform:auth:grant:activate
platform:auth:grant:revoke
platform:auth:grant:query
platform:auth:decision:evaluate
```

## 1. 注册测试资源

调用 `POST /platform/auth/resources`：

```json
{
  "resourceName": "授权测试数据源",
  "resourceType": "govdata:datasource",
  "businessId": "90000001",
  "sourceModule": "govdata-datasource",
  "ownerDeptId": 103,
  "regionCode": "110000",
  "classification": "INTERNAL",
  "securityLevel": 2,
  "attributes": {
    "datasource": {
      "environment": "DEV",
      "accessMode": "READ_ONLY",
      "status": "DISABLED",
      "connectorCode": "mysql"
    }
  }
}
```

记录响应 `data.resourceId`，后续示例用 `${resourceId}` 表示。重复提交相同
`sourceModule + resourceType + businessId` 会幂等更新，不会创建第二条资源。

## 2. 验证默认拒绝

调用 `POST /platform/auth/decisions/evaluate`：

```json
{
  "resourceId": "${resourceId}",
  "resourceType": "govdata:datasource",
  "action": "VIEW",
  "purpose": "DATASOURCE_ADMINISTRATION"
}
```

此时应返回 `decision=DENY`、`reasonCode=AUTHZ_DEFAULT_DENY`。

## 3. 发布允许策略

示例部门 ID 为 `103`。请改为当前登录用户所属部门或其祖先部门 ID，并在修改部门后重新登录。

调用 `POST /platform/auth/policies`：

```json
{
  "policyName": "数据源部门管理策略",
  "effect": "PERMIT",
  "riskLevel": "LOW",
  "priority": 100,
  "conditionJson": "{\"logic\":\"ALL\",\"conditions\":[],\"groups\":[]}",
  "obligationJson": "{\"allowedColumns\":[\"name\",\"regionCode\"],\"maskingRules\":{},\"rowFilters\":[],\"maxRows\":100,\"exportAllowed\":false}",
  "subjects": [
    {
      "subjectType": "DEPARTMENT",
      "subjectId": "103"
    }
  ],
  "resources": [
    {
      "resourceId": "${resourceId}",
      "includeChildren": false
    }
  ],
  "actions": ["VIEW", "AUTHORIZE"]
}
```

记录响应 `data.policyId`，再调用
`PUT /platform/auth/policies/${policyId}/publish`。重新执行第 2 步，预期：

- `decision=PERMIT`
- `reasonCode=AUTHZ_POLICY_PERMIT`
- `matchedPolicyIds` 包含已发布的 `policyVersionId`
- `obligation.maxRows=100` 且 `exportAllowed=false`

## 4. 验证 Grant 生命周期

先使用动作 `TEST_CONNECTION` 重复第 2 步，应保持默认拒绝。然后调用
`POST /platform/auth/grants`。时间必须使用 ISO `LocalDateTime` 格式，中间是 `T`：

```json
{
  "granteeType": "HUMAN",
  "granteeId": "1",
  "purpose": "DATASOURCE_ADMINISTRATION",
  "effectiveTime": "2026-08-02T00:00:00",
  "expireTime": "2099-12-31T23:59:59",
  "scopes": [
    {
      "resourceId": "${resourceId}",
      "actionCode": "TEST_CONNECTION",
      "includeChildren": false,
      "obligationJson": "{\"allowedColumns\":[\"name\"],\"maskingRules\":{},\"rowFilters\":[],\"maxRows\":10,\"exportAllowed\":true}"
    }
  ]
}
```

记录 `data.grantId`，依次验证：

HTTP 管理入口会忽略客户端的 source/grantor 字段，由服务端生成 `ADMIN` 来源并绑定当前登录人。

1. 草稿状态调用 `TEST_CONNECTION`：仍为默认拒绝。
2. 调用 `PUT /platform/auth/grants/${grantId}/activate`。
3. 再调用 `TEST_CONNECTION`：返回 `AUTHZ_GRANT_PERMIT`，`matchedGrantIds` 包含该 Grant。
4. 调用 `PUT /platform/auth/grants/${grantId}/revoke`，请求体为 `{"reason":"闭环验证结束"}`。
5. 再调用 `TEST_CONNECTION`：恢复 `AUTHZ_DEFAULT_DENY`。

## 5. 检查审计

在当前租户内检查最近的决策：

```sql
SELECT decision_id,
       subject_id,
       resource_code_snapshot,
       action_code,
       decision,
       reason_code,
       trace_id,
       occurred_time
FROM authz_decision_log
ORDER BY occurred_time DESC
LIMIT 20;
```

应至少看到 VIEW 与 TEST_CONNECTION 的拒绝、允许、撤销后拒绝记录，并能使用 `trace_id`
关联应用日志。

## 当前边界

- 本验证证明授权决策闭环可运行，不代表查询约束已经作用到外部数据库。
- `rowFilters`、列投影、脱敏、限量和禁导出将在 `govdata-query` 中强制执行；在此之前不能把
  `PERMIT` 结果直接等同为可以绕过查询网关访问数据。
- 当前没有授权管理前端页面，下一阶段可基于现有接口补分页、模拟、停用及页面配置。

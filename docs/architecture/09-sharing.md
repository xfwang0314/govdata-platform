# 数据共享架构

- 状态：架构基线 v1（已确认）
- 适用范围：租户内部部门间共享
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 第一版范围

共享对象包括 Dataset、API 和 FileResource，参与方包括提供部门、使用部门、申请人、审批人和
订阅主体。第一版不支持跨租户共享，也不将公共数据开放混入部门共享。

## 核心模型

SharingCatalogItem、SharingRequest、SharingApproval、SharingAgreement、SharingSubscription、
DeliveryPlan、UsageRecord、RevocationRecord。

## 生命周期

```text
DRAFT → REVIEWING → APPROVED → ACTIVATING → ACTIVE
ACTIVE → REVOKING → REVOKED
```

审批通过只是业务事实；只有 `platform-auth` 成功创建 AuthorizationGrant 后才进入 ACTIVE。
撤销在 Grant 禁用和缓存清理确认后完成。创建和撤销使用稳定请求 ID 保证幂等。

## 交付

- 平台内查询：生成 Resource Grant，经 `govdata-query` 执行。
- API：创建服务订阅和 Grant，由 `govdata-serving` 执行。
- 文件：生成受控文件 Resource 和下载 Grant。
- 数据复制：创建 ExchangeJob，由 SeaTunnel 执行。

申请必须包含用途、期限、字段、使用方式和责任。敏感字段可以触发多级审批。用途变化、到期、
违规、主体或资源禁用均使授权失效。


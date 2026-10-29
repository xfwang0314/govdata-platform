# 错误编码规范

- 状态：v1（强制）
- 适用范围：`platform-*`、`govdata-*` 自研模块
- 所有者：GovData Platform Team
- 最后更新：2026-08-21

## 规则

- 业务失败统一抛出 `new ServiceException(ErrorCode)` 或 `new ServiceException(ErrorCode, message)`；客户端依据响应的 `errorCode` 判断，不解析 `msg`。
- 格式、必填和 Bean Validation 错误可使用全局校验错误；资源不存在、授权拒绝、状态流转、并发冲突、外部能力失败等领域结果必须使用所属模块稳定错误码。
- 编码格式为 `GOV-{MODULE}-{HTTP状态}`，同一 HTTP 状态下的细分项增加 `-{序号}`；编码一经发布不得改义或复用。
- `message` 可以针对当前上下文补充安全的中文说明，但不得泄漏 Token、密文、连接串、SQL、外部堆栈或其他租户信息。
- 外部异常必须在 Connector/Adapter 边界映射为领域错误码；详细原因只进入受控日志，并关联 `traceId`。

## 当前目录

| 模块 | 错误码定义 | 覆盖重点 |
|---|---|---|
| platform-auth | `AuthorizationErrorCode` | 授权拒绝、资源上下文、投影未就绪、租户边界 |
| platform-secret | `SecretErrorCode` | 不存在、重复、主密钥缺失、凭据不可用 |
| platform-mcp | `McpErrorCode` | 凭据认证/状态/访问、工具范围、高风险确认、配额 |
| govdata-datasource | `DataSourceErrorCode` | 数据源、Connector、Secret、连接失败 |
| govdata-query | `QueryErrorCode` | 查询契约、授权、字段、Schema、Adapter、执行失败 |
| govdata-metadata | `MetadataErrorCode` | 观察批次、父资产、资产可见性、修订与资源投影 |
| govdata-metadata-harvest | `HarvestErrorCode` | 单数据源计划配置/状态、数据源运行互斥、外部发现、原始结果大小与归档失败 |

新增模块必须在其 `domain.exception` 创建自己的目录，并在模块测试中断言关键失败场景的 `errorCode`。

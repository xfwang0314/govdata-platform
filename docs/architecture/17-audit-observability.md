# 审计与可观测性

- 状态：架构基线 v1（已确认）
- 适用范围：日志、审计、指标和链路追踪
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 分类

TechnicalLog、OperationAudit、AuthorizationDecisionAudit、DataAccessAudit 和 EngineRunLog 分开存储，
但共享 auditId、tenantId、traceId、actor、module、action、resource、purpose、result、reasonCode、
occurredAt 和 duration 等通用字段。

授权审计补充 Policy/Grant/Delegation/Obligation；数据访问审计补充字段集合、行数、脱敏和查询指纹；
引擎运行日志补充 engineType、jobId、runId、输入输出指标和失败编码。

## 规则

- 审计追加写，不原地修改或逻辑删除。
- HTTP、MCP、授权、查询和引擎执行传递 traceId；任务另外使用 runId。
- 保存主体和资源必要快照，但不记录密码、Token、私钥、完整敏感值或完整结果集。
- 拒绝决策同样审计；高风险操作产生安全告警。
- 不同审计类型配置独立保留和归档策略。
- 高风险数据操作在审计无法可靠写入时默认失败或进入可靠缓冲。
- 后续可以接 OpenTelemetry 和集中日志平台，但领域审计结构保持稳定。


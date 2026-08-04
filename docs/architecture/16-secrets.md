# 凭据与 Secret 架构

- 状态：架构基线 v1（已确认）
- 适用范围：数据库密码、SFTP 私钥、API Secret、引擎凭据
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 模型

业务配置只保存 SecretRef。`platform-secret` 通过 SecretProvider 按需解析 SecretValue，第一阶段
使用数据库密文 Adapter，未来可接 Vault、KMS 或密码机。

Secret 记录包含 tenantId、secretType、密文、keyVersion、status 和 expireTime。主加密密钥不得与
密文存放在同一数据库或提交到 Git。

## 生命周期

支持创建、轮换、禁用、过期和审计。轮换后安全刷新连接池；禁用或过期后 Connector 不能继续创建
新连接。前端只显示“已配置”，编辑时不回显旧值。

## 安全不变量

- API、VO、异常和日志不包含 SecretValue。
- 按 tenantId、调用模块和用途授权 Secret 解析。
- MCP Tool、Agent 和 SeaTunnel 任务定义不能获得永久明文。
- SeaTunnel 使用安全变量或短期解析方式注入，平台审计只记录 SecretRef。
- 开发环境不得使用生产 Secret；配置模板只包含占位符。


# 凭据与 Secret 架构

- 状态：架构基线 v1（已确认）
- 适用范围：数据库密码、SFTP 私钥、API Secret、引擎凭据
- 所有者：GovData Platform Team
- 最后更新：2026-08-06

## 模型

业务配置只保存 SecretRef。`platform-secret` 通过 SecretProvider 按需解析 SecretValue，第一阶段
使用数据库 AES-256-GCM 密文 Adapter，未来可接 Vault、KMS 或密码机。主密钥通过
`PLATFORM_SECRET_MASTER_KEY` 注入，值为 Base64 编码的 32 字节随机密钥。

Secret 记录包含 tenantId、secretType、密文、keyVersion、status 和 expireTime。主加密密钥不得与
密文存放在同一数据库或提交到 Git。

`SecretType` 是 `platform-api` 的跨模块稳定契约，同时定义类型 code、中文 label 和字段 Schema。
第一版包含 `USERNAME_PASSWORD`、`PASSWORD`、`TOKEN`、`API_KEY`、`SSH_PRIVATE_KEY`、
`OAUTH2_CLIENT` 和开放键值类型 `KEY_VALUE`。业务模块、平台实现和页面必须消费同一 Schema，不得复制类型编码、
字段名或按类型编写第二套表单分支。

凭据名称用于简短识别；可选 description 用于记录用途、责任边界和维护说明。description 不参与
SecretRef 解析、用途授权或程序匹配，也不得填写密码、Token、连接串等敏感内容。

## 生命周期

支持创建、轮换、禁用、过期和审计。轮换后安全刷新连接池；禁用或过期后 Connector 不能继续创建
新连接。前端只显示“已配置”，编辑时不回显旧值。

## 安全不变量

- API、VO、异常和日志不包含 SecretValue。
- 按 tenantId、调用模块和用途授权 Secret 解析。
- 所属模块和用途由拥有语义的业务模块通过 `SecretUsageContributor` 登记；`SecretUsageCatalog` 启动时
  校验唯一性并生成不可变快照。管理页面和运行时校验使用同一目录，禁止自由输入。
- `platform-secret` 不得硬编码 `govdata-*` 模块和用途；新增用途只修改所属业务模块的枚举、Contributor 和测试。
- `purposeScope` 保存稳定用途编码的逗号分隔集合，运行时与 `SecretResolveRequest.purpose` 精确匹配。
- MCP Tool、Agent 和 SeaTunnel 任务定义不能获得永久明文。
- SeaTunnel 使用安全变量或短期解析方式注入，平台审计只记录 SecretRef。
- 开发环境不得使用生产 Secret；配置模板只包含占位符。
- 数据库与运行时 SecretRef 统一使用 `secret:sec_xxx`，由 `platform-secret` 集中管理与审计。
- Secret 创建和轮换接口的操作日志禁止保存请求和响应参数。

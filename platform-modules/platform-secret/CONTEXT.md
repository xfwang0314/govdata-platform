# platform-secret 模块上下文

本模块拥有 Secret 元数据、不可变密文版本和访问审计，只通过 `SecretProvider` 临时提供 SecretValue。

## 边界

- SecretRef 是业务模块保存的稳定引用，SecretValue 不能进入响应、日志、事件或业务表。
- 创建和轮换写入新版本；禁用、过期或调用范围不匹配时拒绝解析。
- 密文在平台控制库，主密钥必须来自数据库之外；未来通过同一 Interface 接入 Vault/KMS/密码机。
- 页面只管理元数据并在创建/轮换时提交一次值，任何查询都不得回显旧值。
- 可选描述只用于说明用途、责任边界和维护事项，不参与引用、授权和程序匹配，且不得包含敏感值。
- 跨模块凭据类型及最小字段契约由 `platform-api.SecretType` 唯一定义，本模块不得维护第二套类型编码。
- 凭据消费模块和用途由各业务模块通过 `SecretUsageContributor` 贡献；`SecretUsageCatalog` 启动时校验并快照，
  页面选项和运行时校验读取同一目录。本模块禁止硬编码 `govdata-*` 模块或用途。

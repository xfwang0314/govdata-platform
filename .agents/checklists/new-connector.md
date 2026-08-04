# 新 Connector 检查清单

- [ ] Connector 只声明真实 Capability，没有按类型扩散 switch。
- [ ] 配置具有 Schema、校验、默认值和敏感字段标记。
- [ ] 凭据只通过 secretRef/SecretProvider 获取且不会进入日志。
- [ ] 统一处理连接池、超时、取消、资源释放和异常映射。
- [ ] 数据交换优先复用 SeaTunnel Connector，不重复实现执行引擎。
- [ ] 提供合同测试：能力、连接、失败、并发、释放、类型转换、凭据保护。
- [ ] 文档说明支持版本、限制和运维依赖。


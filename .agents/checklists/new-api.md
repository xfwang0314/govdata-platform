# 新接口检查清单

- [ ] 路径、权限、Resource、事件编码遵循命名规范且彼此不混用。
- [ ] 返回 `R<T>` 或 `R<PageResult<View>>`，未创建新响应包装。
- [ ] 未返回 Entity、Page、Wrapper 或敏感配置。
- [ ] 使用 Command/Query/View，时间为 LocalDateTime。
- [ ] 定义 ErrorCode、traceId、幂等和破坏性变化策略。
- [ ] 同时执行 RBAC 和必要的 ResourceAuthorize/ABAC。
- [ ] 参数、行数、超时、导出和审计要求明确。
- [ ] 已添加正常、拒绝、跨租户和异常测试。

# 代码评审检查清单

- [ ] 修改符合需求且没有顺带扩大授权范围。
- [ ] 领域边界、依赖方向和数据所有权未被破坏。
- [ ] Interface 足够小且隐藏实现复杂度，没有浅层透传。
- [ ] R/PageResult、ErrorCode、traceId、LocalDateTime 使用一致。
- [ ] Tenant、RBAC、ABAC、Grant、Delegation 和 Obligation 执行完整。
- [ ] SQL、Secret、日志、事件和 MCP 没有安全旁路。
- [ ] 失败、重试、幂等、缓存失效和最终一致状态已覆盖。
- [ ] 测试验证行为和安全不变量，未测试内部实现细节。
- [ ] SQL、文档、CONTEXT/ADR 与代码同步。
- [ ] Git 差异无 target、日志、IDE、本地配置或无关文件。


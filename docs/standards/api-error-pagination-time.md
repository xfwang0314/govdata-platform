# 接口、异常、分页与时间规范

- 状态：v1（强制）
- 适用范围：HTTP JSON 接口和跨模块契约
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

- 所有 JSON 接口统一返回 `R<T>`；分页统一返回 `R<PageResult<VO>>`。
- 禁止新增 ApiResponse、Result、TableDataInfo 等第二套响应/分页模型。
- `code` 保持当前前端兼容语义；`errorCode` 是稳定机器编码，`msg` 是可变展示文案。
- 前端不得解析 msg 判断错误；未知异常只返回通用消息，堆栈留在服务端。
- ErrorCode 按 `GOV-{DOMAIN}-xxxx` 分段，每个领域维护自己的枚举。
- 响应和日志使用同一个 traceId。
- 创建接口返回新资源稳定 ID；命令接口定义并验证幂等语义。
- Controller 只接收/返回契约对象，不暴露 Entity、MyBatis Page、Wrapper。
- 时间 JSON 统一 ISO 8601：`yyyy-MM-dd'T'HH:mm:ss[.SSS]`，Java 使用 LocalDateTime。
- 非 CRUD 动作使用明确子路径，如 `POST /jobs/{id}/publish`。
- 破坏性接口变化发布新版本或兼容迁移说明。


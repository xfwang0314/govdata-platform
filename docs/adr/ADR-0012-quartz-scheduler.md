# ADR-0012：后台调度采用嵌入式 Quartz

- 状态：已采用
- 日期：2026-08-03

## 决策

后台动态调度统一采用嵌入式 Quartz 与 JDBC JobStore，移除 SnailJob Client、Server、控制台、配置和独立建库脚本。

领域模块拥有计划、运行、状态和审计事实；Quartz 只保存触发基础设施状态。领域代码不得直接操作 `QRTZ_*` 表，也不得把 Quartz JobDetail 当作领域任务模型。

当 `govdata-quality` 出现第一条可执行质量计划时，在 `platform-task` 落稳定的 `TaskSchedulerGateway` 和 Quartz Adapter。接口至少覆盖注册/更新、暂停、恢复、删除和立即触发，并明确时区、misfire、并发和幂等语义。

## 理由

- 当前采用模块化单体，嵌入式调度避免部署独立调度服务。
- 数据质量需要页面动态 Cron、暂停恢复、misfire 和多实例单次触发，Spring `@Scheduled` 不足以承载。
- Quartz JDBC JobStore 支持持久化和集群协调，且 Spring Boot 提供正式集成。

## 约束

- 生产使用 JDBC JobStore，禁止 RAMJobStore。
- Quartz 官方初始化脚本不得配置为每次启动重建；使用仓库不可变升级脚本维护。
- JobDataMap 只保存稳定 ID，不序列化 Entity、Service、Secret 或大 JSON。
- Job 必须幂等，调用领域 Application 接口执行，失败、重试和业务结果写入领域运行表。

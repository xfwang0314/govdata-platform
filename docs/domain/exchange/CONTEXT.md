# 数据交换

- 状态：领域词汇 v1
- 适用范围：数据交换上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

数据交换上下文管理任务定义、发布和运行编排，数据传输由 SeaTunnel 执行。

## Language

**Exchange Job**:
描述交换意图、来源、目标和调度的业务任务。
_Avoid_: SeaTunnel Job

**Exchange Job Version**:
发布后不可变的一份交换任务定义。
_Avoid_: 可编辑配置

**Exchange Run**:
某个任务版本的一次平台运行实例，关联一个引擎作业。
_Avoid_: 调度配置

**Execution Plan**:
由平台任务编译出的引擎中立执行描述。
_Avoid_: SeaTunnel HOCON 业务模型

**Engine Job**:
SeaTunnel 中实际执行传输的作业。
_Avoid_: Exchange Job

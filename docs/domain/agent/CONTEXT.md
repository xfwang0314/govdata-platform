# Agent

- 状态：领域词汇 v1
- 适用范围：Agent 上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

Agent 上下文管理 AI Agent 的独立身份、有限委托、任务、工具能力和执行风险。

## Language

**Agent Identity**:
具有稳定标识、版本、所有者、状态和基础能力的 AGENT Principal。
_Avoid_: 用户账号、模型名称

**Delegator**:
将自身权限有限子集委托给 Agent 的 HUMAN 或 SERVICE Principal。
_Avoid_: 被冒充用户

**Agent Task**:
具有目的、范围、有效期和状态的一次 Agent 工作实例。
_Avoid_: 对话消息

**Tool**:
Agent 可调用的受控平台能力，具有类型化输入、输出和风险等级。
_Avoid_: 任意内部接口

**Tool Action Binding**:
Tool 与 Semantic Action、Authorization Action 及输入输出概念的映射。
_Avoid_: Tool 名称约定

# Agent

- 状态：领域词汇 v1
- 适用范围：Agent 上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-21

Agent 上下文管理 AI Agent 的独立身份、有限委托、任务、工具能力和执行风险。

## Language

**Agent Identity**:
具有稳定标识、版本、所有者、状态和基础能力的 AGENT Principal。
_Avoid_: 用户账号、模型名称

**Delegator**:
将自身权限有限子集委托给 Agent 的 HUMAN 或 SERVICE Principal。
_Avoid_: 被冒充用户

**Delegation**:
Delegator 将自己已经拥有的权限子集，在限定任务、目的、资源、动作和有效期内授予指定 Agent 的可撤销授权关系；
它只能收窄权限，不能创造 Delegator 原本没有的权限。
_Avoid_: Agent Credential、共享授权、长期角色

**Agent Credential**:
用于认证 Agent 调用方并关联归属用户的可轮换访问凭据。凭据只建立身份和工具分类上限，不授予额外资源权限，也不等同于 Delegation。
_Avoid_: 委托授权、用户 Token

**Owner Inheritance**:
未建立显式 Delegation 时，Agent 动态继承归属用户当前拥有的权限，并继续受凭据能力上限和运行时约束收敛；
归属用户权限撤销后，Agent 对应权限同时失效。
_Avoid_: 权限复制、创建时权限快照

**Agent Task**:
具有目的、范围、有效期和状态的一次 Agent 工作实例。
_Avoid_: 对话消息

**Tool**:
Agent 可调用的受控平台能力，具有类型化输入、输出和风险等级。
_Avoid_: 任意内部接口

**Tool Action Binding**:
Tool 与 Semantic Action、Authorization Action 及输入输出概念的映射。
_Avoid_: Tool 名称约定

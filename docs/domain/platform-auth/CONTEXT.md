# 平台授权

- 状态：领域词汇 v1
- 适用范围：平台授权上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-02

平台授权上下文为所有模块提供统一、可审计的运行时授权，不承担登录、共享审批或领域业务。

## Language

**Principal**:
可被认证或授权的主体，类型为 HUMAN、SERVICE 或 AGENT。
_Avoid_: 通用用户、账号对象

**Subject Context**:
一次授权决策使用的主体、角色、部门、租户、密级和委托属性快照。
_Avoid_: LoginUser 透传

**Resource**:
需要独立授权、挂载策略、审计或参与授权关系的领域对象最小投影，仅包含稳定标识和授权属性；
业务数据行不是 Resource。
_Avoid_: 统一业务实体、元数据副本

**Policy**:
对 Subject、Resource、Action、Environment 进行条件判断的版本化规则。
_Avoid_: 权限 SQL、脚本

**Authorization Grant**:
由共享、管理或委托等来源产生的、可运行和可撤销的明确访问授权。
_Avoid_: SharingAgreement、审批记录

**Delegation**:
HUMAN 或 SERVICE 将自身权限的有限子集在限定时间和任务内委托给 AGENT。
_Avoid_: 冒充、Token 转交

**Obligation**:
授权允许后仍必须执行的行过滤、字段投影、脱敏、限量或禁导出约束。
_Avoid_: Allow 标记

**BreakGlass Access**:
经审批、限时且强审计的跨租户应急访问。
_Avoid_: 超级管理员直通

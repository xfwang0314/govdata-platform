# 数据质量架构

- 状态：架构基线 v1（已确认）
- 适用范围：质量规则、检查、问题、整改和评分
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 模型

QualityRuleTemplate、QualityRule、QualityRuleBinding、QualityCheckPlan、QualityRun、
QualityMetric、QualityIssue、RemediationOrder、QualityScore。

规则来源包括 MANUAL、TEMPLATE、STANDARD、PROFILING、IMPORTED 和 PLUGIN。用户可以独立定义
质量任务，不要求先绑定数据标准。规则和任务分离：规则描述检查什么，计划描述资源、规则、阈值、
权重和调度。

## 执行

`QualityExecutionEngine` 是稳定 seam。第一阶段提供结构化规则到只读聚合 SQL 的安全编译和下推；
大规模场景可接外部执行引擎。普通用户不得提交任意 SQL，复杂规则通过受测插件扩展。

质量维度包括完整性、唯一性、有效性、一致性、及时性和准确性。运行绑定规则版本和元数据快照；
Schema 变化使受影响规则进入待验证状态。

## 闭环

```text
规则绑定 → 计划调度 → 运行 → 指标 → 问题 → 整改 → 复检 → 评分
```

默认只保存指标和必要问题定位，不保存完整敏感原始数据；样例必须限量和脱敏。发现问题不等于
自动修改源数据。


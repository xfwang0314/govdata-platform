# 数据质量

- 状态：领域词汇 v1
- 适用范围：数据质量上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

数据质量上下文管理规则、检查、指标、问题、整改和评分闭环，执行引擎可替换。

## Language

**Quality Rule**:
对一个或多个数据属性进行可版本化检查的定义。
_Avoid_: 任意 SQL

**Quality Check Plan**:
将资源、规则、阈值和调度组合成可执行计划。
_Avoid_: Quality Rule

**Quality Run**:
某个检查计划的一次执行实例。
_Avoid_: Quartz Job 直接充当质量任务聚合

**Quality Metric**:
一次运行产生的可度量结果，如空值率或唯一率。
_Avoid_: Quality Score

**Quality Issue**:
指标未达到阈值后形成的、需要跟踪处理的问题。
_Avoid_: 技术异常

**Remediation Order**:
对质量问题进行责任分派、整改和复检的业务记录。
_Avoid_: 自动修复脚本

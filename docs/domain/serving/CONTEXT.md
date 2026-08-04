# 数据服务

- 状态：领域词汇 v1
- 适用范围：数据服务上下文
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

数据服务上下文将受控查询发布为可版本化、可订阅和可审计的数据 API。

## Language

**Data Service**:
面向消费者发布的数据能力定义，拥有稳定编码和多个版本。
_Avoid_: Java API、Controller

**Data Service Version**:
包含输入、输出、查询定义和限制策略的不可变发布版本。
_Avoid_: 当前配置

**Service Endpoint**:
某个服务版本对外暴露的调用入口。
_Avoid_: Resource 本身

**Invocation**:
消费者对数据服务的一次受控调用记录。
_Avoid_: 应用访问日志

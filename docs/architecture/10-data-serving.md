# 数据服务架构

- 状态：架构基线 v1（目标模块）
- 适用范围：数据 API 定义、发布、订阅和调用
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

## 定位

`govdata-serving` 面向数据消费者发布版本化数据 API；它不同于保存 Java 跨模块契约的
`govdata-api`。

## 核心模型

DataService、DataServiceVersion、InputParameter、OutputField、QueryDefinition、
ServiceEndpoint、ServiceSubscription、RateLimitPolicy、ServiceInvocation。

草稿可修改，已发布版本不可变；破坏性变化发布新版本。输入、输出、查询定义、错误码、配额和
限流均属于版本。

## 调用链

```text
调用方认证
→ Subscription/Grant
→ ABAC 与用途校验
→ 输入 Schema 校验
→ govdata-query
→ 行过滤、字段投影与脱敏
→ 结果和用量审计
```

调用方不得提交 SQL，服务定义只引用 Resource 和逻辑字段。OpenAPI 可以由发布版本生成，但不是
业务事实来源。第一阶段运行在统一后端，未来可通过 Adapter 接入独立 API Gateway。


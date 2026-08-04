# Platform Auth 表结构评审

- 状态：第一版核心结构已迁移到 Java 实现与空库基线
- 评审范围：`platform-auth` 第一版 10 张表
- DDL：[01-platform-auth-mysql.sql](../ddl/01-platform-auth-mysql.sql)
- 数据库：MySQL 8.0
- 最后更新：2026-08-02

## 结论

第一版采用 Human 主体的 `Resource + ABAC + AuthorizationGrant` 核心子集，满足租户硬隔离、
版本化 Policy、显式 Grant、结构化 Obligation 和追加式授权审计要求。DDL 可以作为空库目标结构
评审。Java 实现与 `script/sql/ry_auth.sql` 已同步到该结构；该脚本仍是破坏性的空库基线，不能直接
应用到包含旧授权数据的运行库。

SERVICE、AttributeSchema、Agent Delegation 和 BreakGlass 的表定义继续作为后续阶段注释附录保留，
不进入第一版建表、代码迁移和验收范围。

## 表清单与建表顺序

1. `authz_resource`
2. `authz_resource_relation`
3. `authz_policy`
4. `authz_policy_version`
5. `authz_policy_subject`
6. `authz_policy_resource`
7. `authz_policy_action`
8. `authz_grant`
9. `authz_grant_scope`
10. `authz_decision_log`

全部引用关系使用逻辑外键，不建立数据库物理外键。建表顺序仍按聚合和查询依赖排列，便于阅读、
初始化和未来拆分。

## 通过项

- 稳定业务编码在归档后仍不复用，唯一键不包含 `del_flag`。
- 同模块引用显式保存 `tenant_id` 与稳定 ID，由租户插件和应用服务禁止跨租户串联。
- Policy 定义拆为聚合根、不可变版本、主体、资源和动作五类事实。
- Policy 发布服务校验当前版本属于同租户、同一 Policy，数据库不建立循环物理外键。
- `ANY` 主体使用固定键 `*`，避免 MySQL 对 NULL 唯一键不生效。
- 资源 ID/资源类型二选一绑定使用生成列 `resource_selector` 保证唯一。
- Grant 的资源动作范围使用关系表，不使用逗号字符串或范围 JSON。
- 所有引用列保留必要索引；物理外键移除后，由事务、状态机、对账任务和集成测试保证完整性。
- 决策日志追加写，同时记录主体租户和资源租户，拒绝决策也可追溯。
- JSON 仅用于受限条件 DSL、Obligation、注册扩展属性和审计摘要。

## 已完成的 Java 迁移

| 原型结构 | 当前结构 | 已完成调整 |
|---|---|---|
| `authz_policy` 直接保存 effect/actions/condition/obligation | 定义移入 `authz_policy_version`，动作进入 `authz_policy_action` | 决策服务只读取当前已发布不可变版本 |
| `authz_policy_subject.policy_id` | `policy_version_id` | 主体集合随发布版本冻结 |
| `authz_policy_resource.policy_id` | `policy_version_id` | 资源集合随发布版本冻结 |
| `authz_grant_resource.actions` | `authz_grant_scope` 每行一个 Resource + Action | Grant 决策读取结构化 Scope |
| `valid_from/valid_to` | `effective_time/expire_time` | Entity 与生命周期服务已统一命名 |
| 状态主要使用 `char(1)` | 使用稳定英文状态 code | Entity、枚举和查询条件已同步 |
| 决策日志以 `user_id/dept_id` 为主 | 使用 `subject_type/subject_id` 和最小快照 | 决策日志已按目标结构写入 |
| 无 SERVICE、Delegation、BreakGlass、AttributeSchema | 后续阶段独立模型 | 第一版不迁移、不建表、不开放入口 |

## 推荐迁移顺序

1. 保留当前运行表，先新增 PolicyVersion、PolicyAction 等第一版无冲突表。
2. 将现有 Policy 每条数据回填为版本 1，并拆分动作；校验内容哈希和绑定数量。
3. 发布兼容代码，优先读取目标版本结构，旧字段暂时只读保留。
4. 将 `authz_grant_resource` 数据迁移为 `authz_grant_scope`，对每个动作生成独立行。
5. 升级授权决策日志写入模型，完成新旧结果对账。
6. 确认无旧版本代码后，再新增删除旧字段/旧表的后续升级脚本。

迁移不得通过一次破坏性 `DROP/RENAME` 完成。正式升级脚本要等 Java 兼容实现和集成测试完成后，
再进入 `script/sql/upgrade`；当前目标 DDL继续留在设计目录。

## 验证结果

- 第一版包含 10 张执行建表语句，名称完整且无重复。
- 所有表、索引和约束标识符均小于 MySQL 64 字符限制。
- SERVICE、AttributeSchema、Delegation 和 BreakGlass 定义仅作为注释附录保留，不会被 MySQL 执行。
- 当前机器没有 MySQL Client 或 Docker，因此尚未执行真实 MySQL 8 空库建表验证。

## 进入实现前必须补充的测试

- 相同雪花 ID、不同 tenantId 的关系写入必须失败。
- Policy 发布后父表和全部主体/资源/动作子行不可修改。
- 显式 DENY 优先；异常、未知属性和无匹配允许项默认拒绝。
- Grant ACTIVE 后 Scope 不可修改，撤销后缓存失效。
- Human 主体的用户、角色、部门和 ANY 匹配完整。
- `govdata-query` 必须实际执行行过滤、字段投影、脱敏、限量和禁导出 Obligation。
- Policy 与 Grant 管理服务必须覆盖校验、发布/激活、停用/撤销、版本或来源追踪及页面配置。

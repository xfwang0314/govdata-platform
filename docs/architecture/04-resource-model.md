# 统一 Resource 模型

- 状态：架构基线 v1（已确认）
- 适用范围：所有可授权领域对象
- 所有者：GovData Platform Team
- 最后更新：2026-08-02

## 定位

Resource 是领域对象在授权系统中的投影，不是统一业务实体大表。领域模块拥有完整业务模型，
仅向 `platform-auth` 注册稳定标识和授权所需属性。

## 注册粒度

只有满足下列任一条件的对象才注册为 Resource：

- 需要被单独授予或撤销某个 Action。
- 需要独立绑定 Policy、独立审计或独立停用。
- 是授权继承树或资源关系中的稳定节点。

业务数据行不得逐行注册。表内行范围由 ABAC 条件生成结构化 RowFilter，并由 `govdata-query`
执行；文件分片、交换批次等高基数运行数据也优先授权其稳定容器或任务，不为每条明细建 Resource。

字段默认由元数据字段 ID、字段白名单和脱敏 Obligation 管理。只有敏感字段需要独立授权、独立策略
或独立审计时，才把该 Column 注册为 Resource。因此“元数据中存在”不等于“必须进入
`authz_resource`”。

第一版建议注册数据源、数据库/Schema、需要授权的表或数据集、数据 API，以及确需独立控制的
敏感字段。授权读取必须按 Resource ID、稳定业务映射或受索引的主体/类型查询，禁止全表扫描。

## 编码与类型

类型和编码使用可扩展命名空间：

```text
govdata:datasource:{id}
govdata:metadata:table:{id}
govdata:metadata:column:{id}
govdata:dataset:{id}
govdata:serving:api:{id}
govdata:file:{id}
govdata:ontology:concept:{id}
govdata:mcp-tool:{id}
```

展示名称可修改，稳定编码发布后不得复用。领域业务 ID 与 Resource ID 通过注册关系映射。

## 关系

- 数据源、数据库、Schema、表、字段使用父子层级。
- 数据集包含多个表、API 使用多个数据集等关系使用独立 ResourceRelation。
- `parentId` 不表达血缘、语义映射或任意多对多关系。

## 属性

核心属性包含 tenantId、ownerDepartmentId、regionCode、classificationLevel、
sensitivityTags、status。领域扩展属性必须注册 Schema，例如 `quality.issueLevel`。
属性缺失或类型错误时授权默认拒绝。

## 同步与生命周期

领域对象创建或变化后通过契约/事件更新 Resource 投影。删除先进入 DISABLED，再按保留策略归档；
不得物理删除仍被 Policy、Grant、Delegation 或 Audit 引用的 Resource。

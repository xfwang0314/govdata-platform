# govdata-query 模块上下文

开发前阅读：

1. `docs/domain/query/CONTEXT.md`
2. `docs/architecture/06-query-gateway.md`
3. `docs/standards/authorization-security.md`
4. `docs/standards/mysql-sql.md`

模块只依赖 `govdata-api`、`platform-api` 与必要公共基础模块。禁止直接依赖数据源、元数据、共享、
服务或交换实现模块。

外部调用 Interface 是 `AuthorizedQueryGateway`；内部变化通过
`QueryResourceSchemaProvider` 和 `QueryExecutionAdapter` 两个 Adapter seam 隔离。

# 手工数据库升级规范

- 状态：v1（强制，当前不使用 Flyway）
- 适用范围：MySQL 8 初始化和升级脚本
- 所有者：GovData Platform Team
- 最后更新：2026-08-01

```text
script/sql/
├─ baseline/govdata_platform_mysql.sql
├─ upgrade/YYYYMMDD_NNN_description.sql
└─ rollback/（仅必要的高风险变更）
```

- baseline 用于新装，upgrade 用于已部署环境。
- 已发布升级脚本不可修改；新变化新增文件，并同步更新 baseline。
- 每个脚本注明模块、前置版本、影响、锁表风险和验证方式。
- 只包含必要结构/基础数据，不包含演示账号、测试任务或本地配置。
- 每个领域只修改自己拥有的表；跨领域数据迁移单独评审。
- 执行前备份，执行后验证并登记 script_name、checksum、执行人、时间和结果。
- 删除字段采用停止使用、迁移、后续删除的渐进过程。
- 发布清单明确需要执行的脚本顺序；失败优先前向修复，危险回滚需审批。
- CI 至少验证 baseline 可在空 MySQL 8 执行。


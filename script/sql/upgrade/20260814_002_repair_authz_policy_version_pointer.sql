-- module: platform-auth
-- depends-on: 20260814_001_platform_auth_add_discarded_policy_version_status.sql
-- purpose: 修复当前版本与待发布版本错误指向同一记录的历史数据，并固化指针互斥约束
-- expected-rows: 仅影响 current_version_id = draft_version_id 的异常策略组，正常数据零行
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 查询 current_version_id = draft_version_id 的策略组并确认版本状态
-- post-check: 不存在相同版本指针，且 ck_authz_policy_group_version_pointer 已创建
-- rollback-or-forward-fix: 约束可删除；数据修复不建议回滚，异常指针不应恢复

UPDATE authz_policy_group AS policy_group
JOIN authz_policy_group_version AS policy_version
  ON policy_version.tenant_id = policy_group.tenant_id
 AND policy_version.policy_group_version_id = policy_group.draft_version_id
SET policy_group.current_version_id = NULL,
    policy_group.status = 'DRAFT'
WHERE policy_group.current_version_id = policy_group.draft_version_id
  AND policy_version.version_status = 'DRAFT';

UPDATE authz_policy_group AS policy_group
JOIN authz_policy_group_version AS policy_version
  ON policy_version.tenant_id = policy_group.tenant_id
 AND policy_version.policy_group_version_id = policy_group.draft_version_id
SET policy_group.draft_version_id = NULL
WHERE policy_group.current_version_id = policy_group.draft_version_id
  AND policy_version.version_status IN ('PUBLISHED', 'SUPERSEDED', 'DISCARDED');

ALTER TABLE authz_policy_group
    ADD CONSTRAINT ck_authz_policy_group_version_pointer CHECK
        (current_version_id IS NULL OR draft_version_id IS NULL OR current_version_id <> draft_version_id);

SELECT policy_group_id, policy_group_code, current_version_id, draft_version_id, status
FROM authz_policy_group
WHERE current_version_id = draft_version_id;

-- module: platform-auth
-- depends-on: 20260814_002_repair_authz_policy_version_pointer.sql
-- purpose: 修复历史放弃操作残留的 DISCARDED 指针，禁止已放弃版本再次进入发布入口
-- expected-rows: 仅影响仍由 current_version_id 或 draft_version_id 引用的 DISCARDED 异常版本
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: 查询策略组指针引用 DISCARDED 版本的记录
-- post-check: draft_version_id 不再引用 DISCARDED；current_version_id 不再引用 DISCARDED
-- rollback-or-forward-fix: 数据修复不回滚；已放弃的首次版本进入已删除列表，可通过恢复操作继续编辑

-- 兼容上一修复脚本已经把异常版本保留为 current_version_id 的情况。
UPDATE authz_policy_group AS policy_group
JOIN authz_policy_group_version AS policy_version
  ON policy_version.tenant_id = policy_group.tenant_id
 AND policy_version.policy_group_version_id = policy_group.current_version_id
SET policy_group.draft_version_id = policy_group.current_version_id,
    policy_group.current_version_id = NULL,
    policy_group.status = 'DRAFT',
    policy_group.del_flag = '1',
    policy_group.deleted_by = COALESCE(policy_group.deleted_by, policy_group.update_by, policy_group.create_by),
    policy_group.deleted_time = COALESCE(policy_group.deleted_time, CURRENT_TIMESTAMP(3)),
    policy_group.delete_reason = COALESCE(policy_group.delete_reason, '放弃首次待发布版本'),
    policy_version.version_status = 'DRAFT'
WHERE policy_group.draft_version_id IS NULL
  AND policy_version.version_status = 'DISCARDED';

-- 已有合法当前版本时，放弃版本只需清除待发布指针，历史版本记录继续保留。
UPDATE authz_policy_group AS policy_group
JOIN authz_policy_group_version AS policy_version
  ON policy_version.tenant_id = policy_group.tenant_id
 AND policy_version.policy_group_version_id = policy_group.draft_version_id
SET policy_group.draft_version_id = NULL
WHERE policy_group.current_version_id IS NOT NULL
  AND policy_group.current_version_id <> policy_group.draft_version_id
  AND policy_version.version_status = 'DISCARDED';

SELECT policy_group.policy_group_id,
       policy_group.policy_group_code,
       policy_group.current_version_id,
       policy_group.draft_version_id,
       policy_version.version_status
FROM authz_policy_group AS policy_group
JOIN authz_policy_group_version AS policy_version
  ON policy_version.tenant_id = policy_group.tenant_id
 AND policy_version.policy_group_version_id IN
     (policy_group.current_version_id, policy_group.draft_version_id)
WHERE policy_version.version_status = 'DISCARDED';

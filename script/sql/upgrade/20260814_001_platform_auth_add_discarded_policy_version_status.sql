-- module: platform-auth
-- depends-on: 20260813_002_unify_authz_policy_groups.sql
-- purpose: 允许待发布策略版本被标记为 DISCARDED，保留放弃修改的审计事实
-- expected-rows: 0（仅修改 CHECK 约束）
-- lock-risk: LOW
-- downtime-required: NO
-- pre-check: authz_policy_group_version.version_status 仅包含 DRAFT/PUBLISHED/SUPERSEDED
-- post-check: ck_authz_policy_ver_status 允许 DRAFT/PUBLISHED/SUPERSEDED/DISCARDED
-- rollback-or-forward-fix: 回滚前必须确认不存在 DISCARDED 记录；已执行环境优先新增前向修复脚本

ALTER TABLE authz_policy_group_version
    DROP CHECK ck_authz_policy_ver_status;

ALTER TABLE authz_policy_group_version
    ADD CONSTRAINT ck_authz_policy_ver_status
        CHECK (version_status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'DISCARDED'));

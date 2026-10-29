-- module: platform-auth
-- depends-on: 20260821_001_extend_agent_token_lifecycle.sql
-- purpose: 统一Agent凭据更新时间为应用层审计填充
-- expected-rows: authz_agent_token结构变更1列
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: authz_agent_token.update_time存在自动更新时间属性
-- post-check: information_schema.columns.EXTRA不包含on update CURRENT_TIMESTAMP
-- rollback-or-forward-fix: 如需恢复自动更新时间，新增独立前向脚本
-- ============================================================================
-- 模块：platform-auth
-- 前置：已执行 20260821_001_extend_agent_token_lifecycle.sql
-- 影响：移除 Agent 凭据 update_time 的数据库自动更新时间，由应用层/MyBatis 审计字段填充。
-- 锁表风险：MEDIUM；ALTER TABLE 会获取元数据锁，生产执行前确认无长事务。
-- 停机要求：否；建议低峰执行并观察元数据锁等待。
-- 验证：执行后 information_schema.columns.EXTRA 不再包含 on update CURRENT_TIMESTAMP。
-- 回滚：如确有兼容性需要，使用独立前向脚本恢复自动更新时间；不建议恢复。
-- ============================================================================

ALTER TABLE authz_agent_token
    MODIFY COLUMN update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间';

SELECT table_name, column_name, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'authz_agent_token'
  AND column_name = 'update_time';

-- module: platform-secret
-- depends-on: 20260804_001_add_platform_secret_menus.sql
-- purpose: add an optional human-readable management description to platform credentials
-- expected-rows: DDL only; existing rows remain NULL
-- lock-risk: LOW; MySQL 8 instant column append is required
-- downtime-required: NO
-- pre-check: confirm platform_secret exists and description does not exist
-- post-check: confirm description is varchar(500), nullable, and existing values are NULL
-- rollback-or-forward-fix: forward-fix preferred; drop only before any application version writes descriptions

ALTER TABLE platform_secret
    ADD COLUMN description varchar(500) DEFAULT NULL
        COMMENT '凭据用途、责任边界和维护说明，不得填写敏感值',
    ALGORITHM=INSTANT,
    LOCK=NONE;

SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'platform_secret'
  AND column_name = 'description';

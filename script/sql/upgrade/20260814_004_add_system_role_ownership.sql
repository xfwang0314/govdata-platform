-- module: ruoyi-system
-- depends-on: 20260814_003_repair_discarded_policy_pointer.sql
-- purpose: 为部门自治角色管理增加明确权属
-- expected-rows: sys_role 全部存量角色，通常少于 10000 行
-- lock-risk: MEDIUM
-- downtime-required: NO
-- pre-check: 确认每个租户至少存在一个 parent_id=0 的根部门
-- post-check: 确认 owner_dept_id 无 NULL，且 idx_sys_role_owner 已建立
-- rollback-or-forward-fix: 保留已回填权属，通过新的前向脚本修复错误部门

ALTER TABLE sys_role
    ADD COLUMN owner_dept_id bigint DEFAULT NULL COMMENT '角色权属部门ID' AFTER role_sort;

-- 存量数据只在迁移时使用创建部门作为初始权属候选。
UPDATE sys_role
SET owner_dept_id = create_dept
WHERE owner_dept_id IS NULL
  AND create_dept IS NOT NULL;

-- 创建部门缺失的历史角色回填到同租户根部门。
UPDATE sys_role role_record
JOIN (
    SELECT tenant_id, MIN(dept_id) AS root_dept_id
    FROM sys_dept
    WHERE parent_id = 0
    GROUP BY tenant_id
) tenant_root ON tenant_root.tenant_id = role_record.tenant_id
SET role_record.owner_dept_id = tenant_root.root_dept_id
WHERE role_record.owner_dept_id IS NULL;

ALTER TABLE sys_role
    MODIFY COLUMN owner_dept_id bigint NOT NULL COMMENT '角色权属部门ID',
    ADD KEY idx_sys_role_owner (tenant_id, owner_dept_id, status);

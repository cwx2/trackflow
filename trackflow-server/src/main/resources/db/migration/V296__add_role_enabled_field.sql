-- 为角色表添加启用/禁用状态字段
-- 默认所有角色启用（true），内置角色不允许禁用

ALTER TABLE sys_role ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN sys_role.enabled IS '角色是否启用，禁用后不能分配给新用户';

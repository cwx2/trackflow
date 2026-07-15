-- ============================================================
-- V41: 支持项目成员多角色
-- 
-- 背景：project_member 原有 UNIQUE(project_id, user_id) 约束，
--       每个用户在项目中只能有一个角色。
--       改为 UNIQUE(project_id, user_id, role_id) 允许一人多角色。
--
-- 变更：
--   1. 移除旧唯一约束 project_member_project_id_user_id_key
--   2. 新增唯一约束 project_member_project_user_role_key(project_id, user_id, role_id)
--   3. 新增复合索引 idx_pm_project_user 加速成员查询
-- ============================================================

-- 1. 移除旧的唯一约束（一人一角色）
ALTER TABLE project_member DROP CONSTRAINT IF EXISTS project_member_project_id_user_id_key;

-- 2. 新增唯一约束（同一用户在同一项目中不能重复分配相同角色）
ALTER TABLE project_member ADD CONSTRAINT project_member_project_user_role_key 
    UNIQUE(project_id, user_id, role_id);

-- 3. 新增复合索引（按 project_id + user_id 查询场景频繁）
CREATE INDEX IF NOT EXISTS idx_pm_project_user ON project_member(project_id, user_id);

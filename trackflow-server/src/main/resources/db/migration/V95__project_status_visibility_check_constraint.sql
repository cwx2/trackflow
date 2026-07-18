-- V95: 为 project 表添加 status 和 visibility 字段的 CHECK 约束
-- 确保只能写入合法的枚举值，防止数据进入"幽灵状态"

-- 先修正可能存在的非法数据（防止 CHECK 约束添加失败）
UPDATE project SET status = 'active'
WHERE status NOT IN ('active', 'archived');

UPDATE project SET visibility = 'private'
WHERE visibility NOT IN ('private', 'internal', 'public');

-- 添加 CHECK 约束
ALTER TABLE project ADD CONSTRAINT chk_project_status
    CHECK (status IN ('active', 'archived'));

ALTER TABLE project ADD CONSTRAINT chk_project_visibility
    CHECK (visibility IN ('private', 'internal', 'public'));

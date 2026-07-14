-- ============================================================
-- 项目活动日志表：记录项目级别的操作（成员管理、设置变更等）
-- ============================================================

CREATE TABLE project_activity (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),          -- 操作者
    action VARCHAR(50) NOT NULL,                              -- 操作类型: add_member, remove_member, change_role
    target_user_id BIGINT REFERENCES sys_user(id),            -- 被操作的用户
    detail JSONB,                                             -- 扩展信息（如 role 变化）
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引：按项目查活动（常用）
CREATE INDEX idx_project_activity_project_id ON project_activity(project_id, created_at DESC);

-- 索引：按用户查被操作记录
CREATE INDEX idx_project_activity_target_user ON project_activity(target_user_id, created_at DESC);

COMMENT ON TABLE project_activity IS '项目活动日志';
COMMENT ON COLUMN project_activity.action IS '操作类型：add_member/remove_member/change_role/archive/restore';
COMMENT ON COLUMN project_activity.detail IS 'JSON 扩展信息，如 {"role_id":3,"role_name":"developer"} 或 {"old_role_id":3,"new_role_id":2}';

-- =============================================================================
-- V145: Global member table — supports assigning project-level roles globally
-- (i.e., "this user has role X in ALL projects, including future ones")
-- =============================================================================

-- The global_member table stores "global scope" role assignments.
-- A row (user_id=100, role_id=5) means user 100 has role 5 in every project.
-- When a new project is created, the system auto-inserts project_member records
-- for all global_member entries.
CREATE TABLE IF NOT EXISTS global_member (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  BIGINT REFERENCES sys_user(id) ON DELETE SET NULL
);

-- Each (user, role) pair can only exist once
CREATE UNIQUE INDEX IF NOT EXISTS uk_global_member_user_role
    ON global_member (user_id, role_id);

-- Index for looking up all global members (used when creating a new project)
CREATE INDEX IF NOT EXISTS idx_global_member_role
    ON global_member (role_id);

COMMENT ON TABLE global_member IS '全局项目角色分配：用户在所有项目（含未来新建）中拥有指定角色';
COMMENT ON COLUMN global_member.user_id IS '用户ID';
COMMENT ON COLUMN global_member.role_id IS '项目角色ID（必须是 role_type=project 的角色，应用层校验）';
COMMENT ON COLUMN global_member.created_by IS '操作人';

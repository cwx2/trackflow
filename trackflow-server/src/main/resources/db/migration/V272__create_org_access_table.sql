-- V184__create_org_access_table.sql
-- 创建组织级访问控制表，支持在组织层面批量授权角色给用户
-- 授予后，该用户自动获得组织下所有项目的对应角色

CREATE TABLE org_access (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    CONSTRAINT uk_org_access_unique UNIQUE (org_id, user_id, role_id)
);

CREATE INDEX idx_org_access_org ON org_access(org_id);
CREATE INDEX idx_org_access_user ON org_access(user_id);

COMMENT ON TABLE org_access IS '组织级访问控制：授予用户在组织下所有项目的角色';
COMMENT ON COLUMN org_access.org_id IS '组织ID';
COMMENT ON COLUMN org_access.user_id IS '用户ID';
COMMENT ON COLUMN org_access.role_id IS '角色ID';

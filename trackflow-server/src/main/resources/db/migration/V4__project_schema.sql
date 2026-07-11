-- ============================================================
-- 项目表 + 项目成员表
-- ============================================================

CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    key VARCHAR(20) UNIQUE NOT NULL,
    description TEXT,
    org_id BIGINT REFERENCES organization(id),
    lead_id BIGINT REFERENCES sys_user(id),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    issue_sequence INT NOT NULL DEFAULT 0,
    settings JSONB DEFAULT '{}',
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_key ON project(key);
CREATE INDEX idx_project_org ON project(org_id);
CREATE INDEX idx_project_status ON project(status);
CREATE INDEX idx_project_lead ON project(lead_id);

-- 项目成员
CREATE TABLE project_member (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id),
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, user_id)
);

CREATE INDEX idx_pm_project ON project_member(project_id);
CREATE INDEX idx_pm_user ON project_member(user_id);
CREATE INDEX idx_pm_role ON project_member(role_id);

-- ============================================================
-- 报表定义表
-- ============================================================

CREATE TABLE report_definition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}',
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_project ON report_definition(project_id);

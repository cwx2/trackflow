-- ============================================================
-- 保存查询（Saved Query）表
-- ============================================================

CREATE TABLE saved_query (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    folder VARCHAR(100),
    filters JSONB NOT NULL DEFAULT '[]',
    columns JSONB DEFAULT '[]',
    sort_criteria JSONB DEFAULT '[]',
    group_by VARCHAR(50),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_query_user ON saved_query(user_id);
CREATE INDEX idx_query_project ON saved_query(project_id);
CREATE INDEX idx_query_shared ON saved_query(shared);

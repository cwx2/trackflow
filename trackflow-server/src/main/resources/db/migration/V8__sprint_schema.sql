-- ============================================================
-- Sprint / 迭代表
-- ============================================================

CREATE TABLE sprint (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    goal TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'planned',
    start_date DATE,
    end_date DATE,
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sprint_project ON sprint(project_id);
CREATE INDEX idx_sprint_status ON sprint(status);

-- 给 issue 表的 sprint_id 加外键（之前建表时没加因为 sprint 表还不存在）
ALTER TABLE issue ADD CONSTRAINT fk_issue_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id);

-- ============================================================
-- Issue 相关表
-- ============================================================

-- 状态定义
CREATE TABLE issue_status (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    color VARCHAR(20),
    category VARCHAR(20) NOT NULL DEFAULT 'open',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 种子状态
INSERT INTO issue_status (id, name, code, color, category, is_default, is_closed, sort_order) VALUES
(1, 'Open', 'open', '#4CAF50', 'open', true, false, 1),
(2, 'In Progress', 'in_progress', '#2196F3', 'in_progress', false, false, 2),
(3, 'Code Review', 'code_review', '#9C27B0', 'in_progress', false, false, 3),
(4, 'Testing', 'testing', '#FF9800', 'in_progress', false, false, 4),
(5, 'Done', 'done', '#607D8B', 'done', false, true, 5),
(6, 'Cancelled', 'cancelled', '#9E9E9E', 'cancelled', false, true, 6),
(7, 'Reopened', 'reopened', '#F44336', 'open', false, false, 7);

SELECT setval('issue_status_id_seq', 100);

-- Issue 主表
CREATE TABLE issue (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    issue_key VARCHAR(30) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    issue_type VARCHAR(50) NOT NULL DEFAULT 'Task',
    status_id BIGINT NOT NULL REFERENCES issue_status(id),
    priority VARCHAR(20) NOT NULL DEFAULT 'Normal',
    assignee_id BIGINT REFERENCES sys_user(id),
    reporter_id BIGINT NOT NULL REFERENCES sys_user(id),
    sprint_id BIGINT,
    parent_id BIGINT REFERENCES issue(id),
    due_date DATE,
    estimated_hours DECIMAL(8,2),
    spent_hours DECIMAL(8,2) DEFAULT 0,
    custom_fields JSONB DEFAULT '{}',
    resolved_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_issue_key ON issue(issue_key);
CREATE INDEX idx_issue_project ON issue(project_id);
CREATE INDEX idx_issue_status ON issue(status_id);
CREATE INDEX idx_issue_assignee ON issue(assignee_id);
CREATE INDEX idx_issue_reporter ON issue(reporter_id);
CREATE INDEX idx_issue_sprint ON issue(sprint_id);
CREATE INDEX idx_issue_priority ON issue(priority);
CREATE INDEX idx_issue_type ON issue(issue_type);
CREATE INDEX idx_issue_due_date ON issue(due_date);
CREATE INDEX idx_issue_created ON issue(created_at);
CREATE INDEX idx_issue_updated ON issue(updated_at);
CREATE INDEX idx_issue_deleted ON issue(deleted_at);
CREATE INDEX idx_issue_parent ON issue(parent_id);
CREATE INDEX idx_issue_custom_fields ON issue USING GIN (custom_fields);

-- 全文检索索引
CREATE INDEX idx_issue_fulltext ON issue USING GIN (
    to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(description, ''))
);

-- Issue 评论
CREATE TABLE issue_comment (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    content TEXT NOT NULL,
    source VARCHAR(20) DEFAULT 'web',
    email_message_id VARCHAR(200),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_issue ON issue_comment(issue_id);
CREATE INDEX idx_comment_user ON issue_comment(user_id);

-- Issue 附件
CREATE TABLE issue_attachment (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(200),
    uploaded_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attach_issue ON issue_attachment(issue_id);

-- Issue 操作记录
CREATE TABLE issue_activity (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    action VARCHAR(50) NOT NULL,
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    detail JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_issue ON issue_activity(issue_id);
CREATE INDEX idx_activity_created ON issue_activity(created_at);

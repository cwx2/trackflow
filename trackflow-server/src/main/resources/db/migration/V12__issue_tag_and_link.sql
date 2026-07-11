-- ============================================================
-- Issue Tags and Links
-- ============================================================

-- 标签定义（项目级）
CREATE TABLE issue_tag (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20) DEFAULT '#808080',
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, name)
);

CREATE INDEX idx_issue_tag_project ON issue_tag(project_id);

-- Issue 与 Tag 的关联关系
CREATE TABLE issue_tag_relation (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES issue_tag(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(issue_id, tag_id)
);

CREATE INDEX idx_tag_relation_issue ON issue_tag_relation(issue_id);
CREATE INDEX idx_tag_relation_tag ON issue_tag_relation(tag_id);

-- Issue 关联关系
CREATE TABLE issue_link (
    id BIGSERIAL PRIMARY KEY,
    source_issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    target_issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    link_type VARCHAR(50) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(source_issue_id, target_issue_id, link_type)
);

CREATE INDEX idx_issue_link_source ON issue_link(source_issue_id);
CREATE INDEX idx_issue_link_target ON issue_link(target_issue_id);
CREATE INDEX idx_issue_link_type ON issue_link(link_type);

-- V209__add_issue_sprint_table.sql
-- 新增 issue_sprint 关联表，支持工单与多个 Sprint 关联（allowMultipleSprints 配置生效时）。
-- 保留 issue.sprint_id 作为主 Sprint 字段（向后兼容），issue_sprint 为补充关联。

CREATE TABLE issue_sprint (
    id          BIGSERIAL PRIMARY KEY,
    issue_id    BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    sprint_id   BIGINT NOT NULL REFERENCES sprint(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_issue_sprint UNIQUE (issue_id, sprint_id)
);

COMMENT ON TABLE issue_sprint IS '工单-Sprint 多对多关联表（allowMultipleSprints 模式）';
COMMENT ON COLUMN issue_sprint.issue_id IS '工单 ID';
COMMENT ON COLUMN issue_sprint.sprint_id IS 'Sprint ID';
COMMENT ON COLUMN issue_sprint.created_at IS '关联创建时间';

CREATE INDEX idx_issue_sprint_issue_id ON issue_sprint(issue_id);
CREATE INDEX idx_issue_sprint_sprint_id ON issue_sprint(sprint_id);

-- 数据初始化：将现有 issue.sprint_id 同步到关联表（确保数据一致性）
INSERT INTO issue_sprint (issue_id, sprint_id, created_at)
SELECT id, sprint_id, COALESCE(updated_at, created_at, NOW())
FROM issue
WHERE sprint_id IS NOT NULL AND deleted_at IS NULL
ON CONFLICT DO NOTHING;

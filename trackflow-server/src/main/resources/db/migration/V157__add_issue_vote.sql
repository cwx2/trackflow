-- V157__add_issue_vote.sql
-- 为 Issue 模块添加投票（Vote）功能
-- 投票系统允许团队成员对工单投票以表达需求紧迫性

-- 创建 issue_vote 表
CREATE TABLE issue_vote (
    id          BIGSERIAL PRIMARY KEY,
    issue_id    BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_issue_vote UNIQUE(issue_id, user_id)
);

-- 为投票表创建索引，支持按用户和工单快速查询
CREATE INDEX idx_vote_issue ON issue_vote(issue_id);
CREATE INDEX idx_vote_user ON issue_vote(user_id);

-- 在 issue 表添加 vote_count 冗余字段，用于排序和列表展示
ALTER TABLE issue ADD COLUMN vote_count INTEGER NOT NULL DEFAULT 0;

-- 为 vote_count 创建索引，支持按投票数排序
CREATE INDEX idx_issue_vote_count ON issue(vote_count);

COMMENT ON TABLE issue_vote IS '工单投票记录表';
COMMENT ON COLUMN issue_vote.issue_id IS '关联工单ID';
COMMENT ON COLUMN issue_vote.user_id IS '投票用户ID';
COMMENT ON COLUMN issue_vote.created_at IS '投票时间';
COMMENT ON COLUMN issue.vote_count IS '投票数冗余字段，用于排序';

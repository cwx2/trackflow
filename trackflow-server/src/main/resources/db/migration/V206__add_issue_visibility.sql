-- V206__add_issue_visibility.sql
-- 为工单表添加可见性控制字段，支持私密工单功能（对标 YouTrack "Visible to" 字段）
-- public（默认）：项目所有成员可见；restricted：仅限指定用户

-- 1. 为 issue 表增加 visibility 字段
ALTER TABLE issue ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'public';
ALTER TABLE issue ADD CONSTRAINT issue_visibility_check CHECK (visibility IN ('public', 'restricted'));

-- 2. 创建工单可见性用户关联表（当 visibility = restricted 时，记录有权访问的用户）
CREATE TABLE IF NOT EXISTS issue_visibility_user (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT issue_visibility_user_unique UNIQUE (issue_id, user_id)
);

COMMENT ON TABLE issue_visibility_user IS '工单受限可见用户列表（visibility=restricted 时生效）';
COMMENT ON COLUMN issue.visibility IS '工单可见性：public=项目所有成员，restricted=仅限指定用户';
COMMENT ON COLUMN issue_visibility_user.issue_id IS '关联工单 ID';
COMMENT ON COLUMN issue_visibility_user.user_id IS '被授予访问权限的用户 ID';

-- 3. 索引
CREATE INDEX IF NOT EXISTS idx_issue_visibility_user_issue_id ON issue_visibility_user(issue_id);
CREATE INDEX IF NOT EXISTS idx_issue_visibility_issue_id ON issue(id) WHERE visibility = 'restricted';

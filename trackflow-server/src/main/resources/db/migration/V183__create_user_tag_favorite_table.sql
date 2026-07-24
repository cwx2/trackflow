-- V183__create_user_tag_favorite_table.sql
-- 创建用户标签收藏表，支持侧边栏 Tags 分区（对标 YouTrack Issue Sidebar Tags 功能）

CREATE TABLE user_tag_favorite (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL REFERENCES issue_tag(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_tag_favorite UNIQUE (user_id, tag_id)
);

COMMENT ON TABLE user_tag_favorite IS '用户标签收藏 — 侧边栏 Tags 分区的数据源';
COMMENT ON COLUMN user_tag_favorite.user_id IS '用户ID（关联 sys_user）';
COMMENT ON COLUMN user_tag_favorite.tag_id IS '标签ID（关联 issue_tag）';
COMMENT ON COLUMN user_tag_favorite.sort_order IS '排序序号（支持拖拽排序）';

CREATE INDEX idx_user_tag_favorite_user_id ON user_tag_favorite(user_id);

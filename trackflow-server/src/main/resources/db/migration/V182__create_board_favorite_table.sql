-- V182__create_board_favorite_table.sql
-- 创建看板收藏表，支持用户收藏常用看板，Board Selector 中收藏的看板排在列表顶部
-- 修复 REQ-324：原 V156__board_favorite.sql 版本号与已执行的 V156 冲突，表从未创建

CREATE TABLE board_favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Each user can only favorite a board (project) once
CREATE UNIQUE INDEX idx_board_favorite_unique
    ON board_favorite(user_id, project_id);

-- Query favorites by user
CREATE INDEX idx_board_favorite_user
    ON board_favorite(user_id);

COMMENT ON TABLE board_favorite IS '看板收藏表 - 记录用户收藏的看板（项目级别）';
COMMENT ON COLUMN board_favorite.user_id IS '用户ID';
COMMENT ON COLUMN board_favorite.project_id IS '项目ID（一个项目对应一个看板）';
COMMENT ON COLUMN board_favorite.created_at IS '收藏时间';

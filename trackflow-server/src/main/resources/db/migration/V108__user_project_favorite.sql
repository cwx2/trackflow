-- 用户项目收藏表（个人级别收藏，用于项目列表置顶和工单创建时优先展示）
CREATE TABLE user_project_favorite (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    project_id  BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_user_project_favorite UNIQUE (user_id, project_id)
);

-- 按用户查询收藏列表
CREATE INDEX idx_upf_user_id ON user_project_favorite(user_id);
-- 按项目查询（用于项目删除时级联、或统计收藏人数）
CREATE INDEX idx_upf_project_id ON user_project_favorite(project_id);

COMMENT ON TABLE user_project_favorite IS '用户项目收藏（个人级别，用于项目列表置顶）';
COMMENT ON COLUMN user_project_favorite.user_id IS '用户ID';
COMMENT ON COLUMN user_project_favorite.project_id IS '项目ID';
COMMENT ON COLUMN user_project_favorite.created_at IS '收藏时间';

-- ============================================================
-- V138: Dashboard Favorite & Default mechanism
-- REQ-635: 仪表盘收藏 + 默认仪表盘
-- ============================================================

-- 用户对仪表盘的收藏关系（含默认仪表盘标记）
CREATE TABLE dashboard_favorite (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    dashboard_id BIGINT NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一约束：同一用户对同一仪表盘只有一条记录
CREATE UNIQUE INDEX idx_dashboard_favorite_unique
    ON dashboard_favorite(user_id, dashboard_id);

-- 查询某用户的收藏列表
CREATE INDEX idx_dashboard_favorite_user
    ON dashboard_favorite(user_id);

-- 确保每个用户最多只有一个默认仪表盘（部分唯一索引）
CREATE UNIQUE INDEX idx_dashboard_favorite_user_default
    ON dashboard_favorite(user_id) WHERE is_default = true;

COMMENT ON TABLE dashboard_favorite IS '用户仪表盘收藏';
COMMENT ON COLUMN dashboard_favorite.user_id IS '用户ID';
COMMENT ON COLUMN dashboard_favorite.dashboard_id IS '仪表盘ID';
COMMENT ON COLUMN dashboard_favorite.is_default IS '是否为用户的默认仪表盘（每用户最多一个）';

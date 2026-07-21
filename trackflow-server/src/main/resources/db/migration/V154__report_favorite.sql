-- ============================================================
-- V154: Report Favorite mechanism
-- REQ-24: 报表收藏功能，与 dashboard_favorite 对齐
-- ============================================================

-- 用户对报表的收藏关系
CREATE TABLE report_favorite (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    report_id   BIGINT NOT NULL REFERENCES report_definition(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一约束：同一用户对同一报表只有一条记录
CREATE UNIQUE INDEX idx_report_favorite_unique
    ON report_favorite(user_id, report_id);

-- 查询某用户的收藏列表
CREATE INDEX idx_report_favorite_user
    ON report_favorite(user_id);

COMMENT ON TABLE report_favorite IS '用户报表收藏';
COMMENT ON COLUMN report_favorite.user_id IS '用户ID';
COMMENT ON COLUMN report_favorite.report_id IS '报表ID';

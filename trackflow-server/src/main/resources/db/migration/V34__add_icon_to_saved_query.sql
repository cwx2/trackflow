-- ============================================================
-- V34: 为 saved_query 表添加 icon 字段
-- 支持用户为保存的查询设置自定义图标（emoji 或图标标识）
-- ============================================================

ALTER TABLE saved_query ADD COLUMN icon VARCHAR(50) DEFAULT NULL;

-- 为已有的"待我测试"查询设置默认图标
UPDATE saved_query SET icon = '🧪' WHERE name = '待我测试';

COMMENT ON COLUMN saved_query.icon IS '查询图标（emoji 或图标标识），为空则不显示图标';

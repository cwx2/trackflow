-- ============================================================
-- 项目级自定义字段管理：增加排序支持
-- ============================================================

-- 为 custom_field_project 表添加 position 列，支持项目级字段排序
ALTER TABLE custom_field_project ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN custom_field_project.position IS '字段在项目中的显示顺序（覆盖全局 position）';

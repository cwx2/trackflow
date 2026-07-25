-- V161__add_custom_field_auto_attach.sql
-- 为 custom_field_definition 表新增 is_auto_attach 列
-- 实现 YouTrack 的 "Auto-attach to new projects" 行为：
-- 当字段设置 is_auto_attach=true 时，新创建的项目会自动关联该字段
-- 这与 is_for_all 的"全局不可移除"语义不同

ALTER TABLE custom_field_definition ADD COLUMN is_auto_attach BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN custom_field_definition.is_auto_attach IS '是否自动附加到新创建的项目（YouTrack Auto-attach 行为）';

-- 将现有 is_for_all=true 的字段同时设为 is_auto_attach=true
-- 这样在后续阶段可以逐步用 is_auto_attach 替代 is_for_all
UPDATE custom_field_definition SET is_auto_attach = true WHERE is_for_all = true;

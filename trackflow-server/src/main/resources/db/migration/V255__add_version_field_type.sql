-- V255__add_version_field_type.sql
-- 为 custom_field_option 增加版本类型专用属性（release_date, is_released），
-- 支持 Fix versions / Affected versions 字段。

-- 1. 添加 release_date 列（版本发布日期）
ALTER TABLE custom_field_option ADD COLUMN release_date DATE;
COMMENT ON COLUMN custom_field_option.release_date IS '版本发布日期（仅 version 类型使用），实际或预计发布日期';

-- 2. 添加 is_released 列（是否已发布）
ALTER TABLE custom_field_option ADD COLUMN is_released BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN custom_field_option.is_released IS '是否已正式发布（仅 version 类型使用）。影响下拉排序：Fix versions 中未发布版本排前，Affected versions 中已发布版本排前';

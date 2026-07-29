-- V215__add_description_to_custom_field_option.sql
-- 为自定义字段选项增加描述字段，用于在下拉选择时展示 tooltip 说明文字

ALTER TABLE custom_field_option ADD COLUMN IF NOT EXISTS description VARCHAR(1024);

COMMENT ON COLUMN custom_field_option.description IS '选项描述，在下拉选择时以 tooltip 形式展示，帮助用户理解选项含义';

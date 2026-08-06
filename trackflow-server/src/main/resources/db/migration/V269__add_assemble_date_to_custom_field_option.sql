-- V269__add_assemble_date_to_custom_field_option.sql
-- 为 custom_field_option 表增加 assemble_date 字段，支持 build 类型自定义字段

ALTER TABLE custom_field_option ADD COLUMN assemble_date DATE;
COMMENT ON COLUMN custom_field_option.assemble_date IS '构建生成日期（仅 build 类型使用）';

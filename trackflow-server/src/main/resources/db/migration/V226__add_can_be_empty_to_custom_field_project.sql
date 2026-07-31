-- V226__add_can_be_empty_to_custom_field_project.sql
-- 为 custom_field_project 表添加 can_be_empty 字段，支持 YouTrack 风格的"无默认值但必填"配置
-- 
-- 业务说明：
-- 1. can_be_empty = true (默认): 字段可以为空，如果配置了默认值会自动应用
-- 2. can_be_empty = false: 字段不能为空（Empty Value = "Cannot be empty"）
--    - 如果同时 default_value 为 NULL，则表示"无默认值但必填"（YouTrack "No value (required)" 行为）
--    - 此时系统不会自动应用选项表中 isDefault=true 的选项，用户必须主动选择
--    - 前端应显示 "Set value" 提示引导用户填写

ALTER TABLE custom_field_project
ADD COLUMN can_be_empty BOOLEAN NOT NULL DEFAULT true;

-- 添加注释
COMMENT ON COLUMN custom_field_project.can_be_empty IS 
  '是否允许字段为空（项目级覆盖）。false = Cannot be empty，此时若 default_value 也为空则为"无默认值但必填"模式';

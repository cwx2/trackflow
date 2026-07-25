-- V193: 为 board_card_config 表添加字段显示模式配置列
-- 支持每个字段独立配置 Full name / Initial 显示格式（对标 YouTrack Cards Tab）

ALTER TABLE board_card_config
    ADD COLUMN IF NOT EXISTS field_display_modes JSONB;

COMMENT ON COLUMN board_card_config.field_display_modes IS
    '每个字段的显示格式，JSONB 对象，key 为字段名，value 为 "full_name" 或 "initial"。
     示例：{"assignee":"initial","priority":"full_name"}。
     未配置的字段默认使用 full_name 模式。';

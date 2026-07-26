-- V201__board_card_show_custom_field_colors.sql
-- 为 board_card_config 表增加 show_custom_field_colors 字段
-- 对应 YouTrack Board Settings > Cards Tab 的 "Show colors for other custom fields" 开关

ALTER TABLE board_card_config ADD COLUMN IF NOT EXISTS show_custom_field_colors BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN board_card_config.show_custom_field_colors IS
    '是否在卡片自定义字段值旁显示颜色指示器。对应 YouTrack "Show colors for other custom fields"，默认开启。';

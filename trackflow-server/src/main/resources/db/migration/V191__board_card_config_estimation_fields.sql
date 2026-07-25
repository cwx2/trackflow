-- V191__board_card_config_estimation_fields.sql
-- 为 board_card_config 表新增两个估算字段配置列（YouTrack Cards Tab 对标）
-- current_estimation_field_id: 当前估算字段，卡片上显示可编辑估算值
-- original_estimation_field_id: 原始估算字段，Sprint 开始时快照，不可编辑

ALTER TABLE board_card_config
    ADD COLUMN current_estimation_field_id BIGINT,
    ADD COLUMN original_estimation_field_id BIGINT;

COMMENT ON COLUMN board_card_config.current_estimation_field_id IS '当前估算字段 ID（引用 custom_field_definition），对应 YouTrack Current estimation field';
COMMENT ON COLUMN board_card_config.original_estimation_field_id IS '原始估算字段 ID（引用 custom_field_definition），对应 YouTrack Original estimation field（Sprint 开始时快照值）';

-- V156__board_swimlane_selected_values.sql
-- 为看板泳道配置增加值选择功能，对标 YouTrack 的 Swimlane Values Selection
-- 支持选择具体值子集展示为泳道行，以及"未分类"泳道控制

ALTER TABLE board_swimlane_config
    ADD COLUMN selected_values JSONB DEFAULT NULL,
    ADD COLUMN show_uncategorized BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN uncategorized_position VARCHAR(10) NOT NULL DEFAULT 'bottom';

COMMENT ON COLUMN board_swimlane_config.selected_values IS '选中的泳道值列表(JSONB数组)，null表示全选（向后兼容）';
COMMENT ON COLUMN board_swimlane_config.show_uncategorized IS '是否显示"未分类"泳道（不匹配任何选中值的工单）';
COMMENT ON COLUMN board_swimlane_config.uncategorized_position IS '未分类泳道位置：top 或 bottom';

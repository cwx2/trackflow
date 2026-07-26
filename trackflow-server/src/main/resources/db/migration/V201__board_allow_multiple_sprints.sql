-- V201__board_allow_multiple_sprints.sql
-- 为 board_general_config 表增加「允许卡片分配到多个迭代」配置项
-- 对应 YouTrack Board Settings > Cards Tab: Allow cards to be assigned to multiple sprints

ALTER TABLE board_general_config
    ADD COLUMN IF NOT EXISTS allow_multiple_sprints BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN board_general_config.allow_multiple_sprints
    IS '是否允许卡片分配到多个迭代（对应 YouTrack: Allow cards to be assigned to multiple sprints）';

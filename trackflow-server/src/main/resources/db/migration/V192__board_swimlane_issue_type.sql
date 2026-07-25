-- V192__board_swimlane_issue_type.sql
-- 为看板泳道配置表新增 swimlane_issue_type 字段，支持 Issues 模式（按父工单分组泳道）
-- 该字段指定作为泳道行的父工单类型（如 Epic、Feature），仅当 group_by_field = 'parent' 时生效

ALTER TABLE board_swimlane_config
    ADD COLUMN IF NOT EXISTS swimlane_issue_type VARCHAR(50);

COMMENT ON COLUMN board_swimlane_config.swimlane_issue_type IS 'Issues 模式下作为泳道行的工单类型（如 Epic/Feature），仅 group_by_field=parent 时有效';

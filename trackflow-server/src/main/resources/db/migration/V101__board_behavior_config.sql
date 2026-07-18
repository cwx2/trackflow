-- 看板 Board Behavior 配置字段
-- 新增 filter_mode 和 done_retention_days 到 board_general_config 表
-- filter_mode: 'all' (默认，显示全部) | 'active_sprint' (仅当前活跃 Sprint 工单)
-- done_retention_days: 已完成工单保留天数（NULL 表示不限制）

ALTER TABLE board_general_config
    ADD COLUMN filter_mode VARCHAR(20) NOT NULL DEFAULT 'all',
    ADD COLUMN done_retention_days INTEGER DEFAULT NULL;

-- 约束：filter_mode 只允许特定值
ALTER TABLE board_general_config
    ADD CONSTRAINT chk_board_filter_mode CHECK (filter_mode IN ('all', 'active_sprint'));

-- 约束：done_retention_days 必须为正整数或 NULL
ALTER TABLE board_general_config
    ADD CONSTRAINT chk_board_done_retention_days CHECK (done_retention_days IS NULL OR done_retention_days > 0);

COMMENT ON COLUMN board_general_config.filter_mode IS '看板过滤模式：all=显示所有工单, active_sprint=仅显示活跃Sprint工单';
COMMENT ON COLUMN board_general_config.done_retention_days IS '已完成工单保留天数，NULL表示不限制';

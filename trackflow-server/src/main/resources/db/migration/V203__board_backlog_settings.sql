-- V203__board_backlog_settings.sql
-- 为 board_general_config 表添加 Backlog 配置字段（视图模式 + 关联的保存搜索）

ALTER TABLE board_general_config
    ADD COLUMN IF NOT EXISTS backlog_view_mode VARCHAR(10) DEFAULT 'list',
    ADD COLUMN IF NOT EXISTS backlog_saved_query_id BIGINT;

COMMENT ON COLUMN board_general_config.backlog_view_mode IS 'Backlog 视图模式：list=平铺列表（默认）, tree=树形层级';
COMMENT ON COLUMN board_general_config.backlog_saved_query_id IS '过滤 Backlog 工单的保存搜索 ID（null 表示使用默认过滤：不在看板上的工单）';

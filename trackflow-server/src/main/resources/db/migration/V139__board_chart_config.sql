-- =====================================================
-- V139: Board Chart Configuration
-- 看板图表配置表（燃尽图/累积流图）
-- =====================================================

CREATE TABLE board_chart_config (
    id              BIGSERIAL       PRIMARY KEY,
    project_id      BIGINT          NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    -- 图表类型: burndown / cumulative_flow
    chart_type      VARCHAR(30)     NOT NULL DEFAULT 'burndown',
    -- Burndown 计算方式: issue_count / estimation / work_items
    burndown_calculation VARCHAR(30) NOT NULL DEFAULT 'issue_count',
    -- Issue 过滤器模式: all_cards / custom
    issue_filter_mode VARCHAR(20)   NOT NULL DEFAULT 'all_cards',
    -- 自定义过滤条件（当 issue_filter_mode='custom' 时使用）
    issue_filter_query TEXT,
    -- 当前估算字段 ID（引用 custom_field_definition.id，可选）
    estimation_field_id BIGINT,
    -- 原始估算字段 ID（用于 Burndown 偏差计算，可选）
    original_estimation_field_id BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_board_chart_config_project UNIQUE (project_id),
    CONSTRAINT ck_chart_type CHECK (chart_type IN ('burndown', 'cumulative_flow')),
    CONSTRAINT ck_burndown_calculation CHECK (burndown_calculation IN ('issue_count', 'estimation', 'work_items')),
    CONSTRAINT ck_issue_filter_mode CHECK (issue_filter_mode IN ('all_cards', 'custom'))
);

COMMENT ON TABLE board_chart_config IS '看板图表配置（项目级，一个项目一条记录）';
COMMENT ON COLUMN board_chart_config.chart_type IS '图表类型: burndown=燃尽图, cumulative_flow=累积流图';
COMMENT ON COLUMN board_chart_config.burndown_calculation IS 'Burndown计算方式: issue_count=工单数, estimation=估算值, work_items=工时';
COMMENT ON COLUMN board_chart_config.issue_filter_mode IS 'Issue过滤模式: all_cards=所有卡片, custom=自定义查询';
COMMENT ON COLUMN board_chart_config.issue_filter_query IS '自定义过滤条件（查询表达式）';
COMMENT ON COLUMN board_chart_config.estimation_field_id IS '当前估算字段（自定义字段ID）';
COMMENT ON COLUMN board_chart_config.original_estimation_field_id IS '原始估算字段（用于偏差计算）';

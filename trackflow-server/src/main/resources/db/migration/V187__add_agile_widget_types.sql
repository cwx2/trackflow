-- ============================================================
-- V187: Add Agile Chart and Agile Board Status widget types
-- REQ-367: 仪表盘新增敏捷图表和看板状态 Widget
-- ============================================================

-- Drop existing constraint and recreate with new types
ALTER TABLE dashboard_widget DROP CONSTRAINT IF EXISTS chk_widget_type;

ALTER TABLE dashboard_widget
    ADD CONSTRAINT chk_widget_type
    CHECK (widget_type IN (
        'note', 'number_card', 'report_distribution', 'issue_list',
        'activity_feed', 'report', 'sprint_progress', 'calendar',
        'agile_chart', 'agile_board_status'
    ));

COMMENT ON CONSTRAINT chk_widget_type ON dashboard_widget IS '限制微件类型为合法枚举值（含敏捷图表类型）';

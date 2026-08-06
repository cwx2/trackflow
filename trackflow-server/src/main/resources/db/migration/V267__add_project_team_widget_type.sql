-- V267: Add project_team widget type to CHECK constraint
-- REQ-287: 仪表盘新增「项目成员」Widget

ALTER TABLE dashboard_widget DROP CONSTRAINT IF EXISTS chk_widget_type;

ALTER TABLE dashboard_widget
    ADD CONSTRAINT chk_widget_type
    CHECK (widget_type IN (
        'note', 'number_card', 'report_distribution', 'issue_list',
        'activity_feed', 'report', 'sprint_progress', 'calendar',
        'agile_chart', 'agile_board_status', 'project_team'
    ));

COMMENT ON CONSTRAINT chk_widget_type ON dashboard_widget IS '限制微件类型为合法枚举值（含项目成员类型）';

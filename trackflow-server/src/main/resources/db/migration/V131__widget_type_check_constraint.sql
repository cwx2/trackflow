-- ============================================================
-- V131: Add CHECK constraint on dashboard_widget.widget_type
-- REQ-568: Restrict widget_type to valid enum values
-- ============================================================

-- First, clean up any invalid widget_type values (if exist)
DELETE FROM dashboard_widget
WHERE widget_type NOT IN (
    'note', 'number_card', 'report_distribution', 'issue_list',
    'activity_feed', 'report', 'sprint_progress', 'calendar'
);

-- Add CHECK constraint
ALTER TABLE dashboard_widget
    ADD CONSTRAINT chk_widget_type
    CHECK (widget_type IN (
        'note', 'number_card', 'report_distribution', 'issue_list',
        'activity_feed', 'report', 'sprint_progress', 'calendar'
    ));

COMMENT ON CONSTRAINT chk_widget_type ON dashboard_widget IS '限制微件类型为合法枚举值';

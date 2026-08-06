-- V268: Remove widget_type CHECK constraint from dashboard_widget table
-- REQ-290: Widget plugin architecture — move from hardcoded enum to extensible registry
-- Validation is now done at the application layer (non-empty check only),
-- allowing third-party plugins to register custom widget types without DB migration.

ALTER TABLE dashboard_widget DROP CONSTRAINT IF EXISTS chk_widget_type;

COMMENT ON COLUMN dashboard_widget.widget_type IS '微件类型标识（由前端 Widget 注册表定义，应用层校验非空）';

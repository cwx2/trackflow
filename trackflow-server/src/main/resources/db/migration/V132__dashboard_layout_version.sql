-- Add layout_version column to dashboard for optimistic locking on layout save
ALTER TABLE dashboard ADD COLUMN layout_version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN dashboard.layout_version IS 'Optimistic lock version for layout updates, incremented on each layout save';
